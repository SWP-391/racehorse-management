package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.common.TestClock;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** How locks, pending requests and archiving affect sessions (BR-007, BR-013, BR-020 to BR-023). */
class LocksAndSessionsTest extends TrainingTestBase {

    @Test
    void AC_070_statusChangeBlocksThreeSessionsUnchangedAndNotifies() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        List<Long> ids = List.of(schedule(plan, today()), schedule(plan, today().plusDays(2)),
                schedule(plan, today().plusDays(4)));
        List<String> datesBefore = ids.stream().map(id -> {
            try {
                return session(id).get("date").asText();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();

        setStatus(horse, "INJURED");

        for (int i = 0; i < ids.size(); i++) {
            JsonNode s = session(ids.get(i));
            assertThat(s.get("state").asText()).isEqualTo("BLOCKED_BY_LOCK");
            assertThat(s.get("date").asText()).isEqualTo(datesBefore.get(i));
            assertThat(s.get("type").asText()).isEqualTo("GALLOP");
        }
        assertThat(health(horse).get("lock").isNull()).isFalse();
        assertThat(hasNotification(trainer, "\"" + horseName(horse) + "\" bị khóa huấn luyện")).isTrue();
    }

    @Test
    void AC_073_statusChangeAndLockRefusedWhileTrainingPendingOffered() throws Exception {
        long horse = observedHorse();
        long session = schedule(activePlan(horse), today());
        start(session, true);
        asJson(vet, post("/api/horses/" + horse + "/health/status"), Map.of("status", "INJURED", "reason", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_TRAINING"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("yêu cầu khóa chờ")));
        asJson(vet, post("/api/horses/" + horse + "/lock"), Map.of("reason", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_TRAINING"));
        assertThat(health(horse).get("status").asText()).isEqualTo("UNDER_OBSERVATION");
    }

    @Test
    void AC_081_resumeAsIsFutureRescheduledPastMissedDueToLock() throws Exception {
        long horse = observedHorse();
        long plan = activePlan(horse);
        long past = schedule(plan, today());
        long future = schedule(plan, today().plusDays(5));
        lock(horse, "chườm đá 2 ngày");
        assertThat(state(past)).isEqualTo("BLOCKED_BY_LOCK");

        TestClock.shift(Duration.ofDays(2));
        asJson(vet, post("/api/horses/" + horse + "/lock/lift"), Map.of("returnLevel", "RESUME_AS_IS"))
                .andExpect(status().isOk());
        TestClock.reset();

        assertThat(state(past)).isEqualTo("MISSED_DUE_TO_LOCK");
        assertThat(state(future)).isEqualTo("SCHEDULED");
        assertThat(hasNotification(trainer, "\"" + horseName(horse) + "\" đã được gỡ khóa")).isTrue();
    }

    @Test
    void AC_082_resumeWithAdjustmentShiftTenDaysVersionRestorable() throws Exception {
        long horse = observedHorse();
        long plan = activePlan(horse);
        long s1 = schedule(plan, today().plusDays(3));
        long s2 = schedule(plan, today().plusDays(6));
        lock(horse, "nghỉ hồi phục");
        asJson(vet, post("/api/horses/" + horse + "/lock/lift"), Map.of("returnLevel", "RESUME_WITH_ADJUSTMENT"))
                .andExpect(status().isOk());
        assertThat(state(s1)).isEqualTo("AWAITING_REVIEW");

        JsonNode preview = body(asJson(trainer, post("/api/plans/" + plan + "/shift"), Map.of("days", 10, "dryRun", true))
                .andExpect(status().isOk()));
        assertThat(preview.size()).isEqualTo(2);
        assertThat(preview.get(0).get("to").asText()).isEqualTo(today().plusDays(13).toString());
        assertThat(session(s1).get("date").asText()).isEqualTo(today().plusDays(3).toString());

        asJson(trainer, post("/api/plans/" + plan + "/shift"), Map.of("days", 10)).andExpect(status().isOk());
        assertThat(session(s1).get("date").asText()).isEqualTo(today().plusDays(13).toString());
        assertThat(session(s2).get("date").asText()).isEqualTo(today().plusDays(16).toString());
        assertThat(state(s1)).isEqualTo("SCHEDULED");

        JsonNode versions = body(as(trainer, get("/api/plans/" + plan + "/versions")));
        assertThat(versions.size()).isEqualTo(2);
        as(trainer, post("/api/plans/" + plan + "/versions/1/restore")).andExpect(status().isOk());
        assertThat(session(s1).get("date").asText()).isEqualTo(today().plusDays(3).toString());
        assertThat(session(s2).get("date").asText()).isEqualTo(today().plusDays(6).toString());
        assertThat(body(as(trainer, get("/api/plans/" + plan + "/versions"))).size()).isEqualTo(3);
    }

    @Test
    void AC_089_pendingRequestVisibleSessionContinues() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        asJson(vet, post("/api/horses/" + horse + "/pending-lock"), Map.of("reason", "nghe tim bất thường"))
                .andExpect(status().isOk());
        JsonNode detail = body(as(trainer, get("/api/sessions/" + session)));
        assertThat(detail.get("lockPendingAtEnd").asBoolean()).isTrue();
        assertThat(detail.get("session").get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(detail.get("session").get("lockDuringSession").asBoolean()).isTrue();
        assertThat(hasNotification(trainer, "sẽ bị khóa khi buổi tập này kết thúc")).isTrue();
    }

    @Test
    void AC_090_appliedWhenSessionEndsSessionsBlockedVetNotified() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long session = schedule(plan, today());
        long later = schedule(plan, today().plusDays(2));
        start(session, false);
        asJson(vet, post("/api/horses/" + horse + "/pending-lock"), Map.of("reason", "sưng khớp")).andExpect(status().isOk());
        end(session);

        JsonNode h = health(horse);
        assertThat(h.get("lock").get("reason").asText()).isEqualTo("sưng khớp");
        assertThat(h.get("pendingLock").isNull()).isTrue();
        assertThat(state(later)).isEqualTo("BLOCKED_BY_LOCK");
        assertThat(hasNotification(vet, "đã có hiệu lực")).isTrue();
        // Q-025 default: an Đủ điều kiện horse is set to Cần theo dõi before the lock (DEC-024 kept).
        assertThat(h.get("status").asText()).isEqualTo("UNDER_OBSERVATION");
    }

    @Test
    void AC_168_eligibleHorseIsSetUnderObservationThenLockedBothAudited() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        asJson(vet, post("/api/horses/" + horse + "/pending-lock"), Map.of("reason", "nghi viêm gân"))
                .andExpect(status().isOk());
        end(session);
        JsonNode h = health(horse);
        assertThat(h.get("status").asText()).isEqualTo("UNDER_OBSERVATION");
        assertThat(h.get("history").toString()).contains("Áp dụng yêu cầu khóa chờ: nghi viêm gân");
        assertThat(h.get("lock").get("reason").asText()).isEqualTo("nghi viêm gân");
        JsonNode audit = body(as(manager, get("/api/audit").param("actorId", vet.id().toString())));
        List<String> actions = java.util.stream.StreamSupport.stream(audit.spliterator(), false)
                .filter(e -> e.get("targetId").asLong() == horse).map(e -> e.get("action").asText()).toList();
        assertThat(actions).contains("HEALTH_STATUS_CHANGED", "LOCK_PLACED");
    }

    @Test
    void AC_091_appliedAtAutoClose() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        asJson(vet, post("/api/horses/" + horse + "/pending-lock"), Map.of("reason", "theo dõi sau tập"))
                .andExpect(status().isOk());
        TestClock.shift(Duration.ofMinutes(121));
        sessionService.autoClose();
        TestClock.reset();
        assertThat(state(session)).isEqualTo("AUTO_CLOSED");
        assertThat(health(horse).get("lock").get("reason").asText()).isEqualTo("theo dõi sau tập");
    }

    @Test
    void AC_092_withdrawnNoLock() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        asJson(vet, post("/api/horses/" + horse + "/pending-lock"), Map.of("reason", "x")).andExpect(status().isOk());
        as(vet, post("/api/horses/" + horse + "/pending-lock/withdraw")).andExpect(status().isOk());
        end(session);
        JsonNode h = health(horse);
        assertThat(h.get("lock").isNull()).isTrue();
        assertThat(h.get("status").asText()).isEqualTo("ELIGIBLE");
    }

    @Test
    void AC_093_readinessUnchangedWhilePending() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        asJson(vet, post("/api/horses/" + horse + "/pending-lock"), Map.of("reason", "x")).andExpect(status().isOk());
        as(owner, get("/api/horses/" + horse + "/readiness")).andExpect(jsonPath("$.readiness").value("READY"));
    }

    @Test
    void AC_094_headTrainerCannotRequestPendingLock() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        asJson(trainer, post("/api/horses/" + horse + "/pending-lock"), Map.of("reason", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void AC_012_archivingCancelsFutureSessionsAndBlocksNewPlans() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long a = schedule(plan, today().plusDays(1));
        long b = schedule(plan, today().plusDays(3));
        asJson(manager, post("/api/horses/" + horse + "/archive"), Map.of("reason", "SOLD")).andExpect(status().isOk());
        for (long id : List.of(a, b)) {
            JsonNode s = session(id);
            assertThat(s.get("state").asText()).isEqualTo("CANCELLED");
            assertThat(s.get("cancelReason").asText()).isEqualTo("ngựa ngừng quản lý");
        }
        asJson(trainer, post("/api/horses/" + horse + "/plans"), Map.of("phases", List.of(
                phase("X", today(), today().plusDays(5))))).andExpect(status().isConflict());
        asJson(trainer, post("/api/plans/" + plan + "/sessions"), sessionBody(today().plusDays(5), "TROT", false))
                .andExpect(status().isConflict());
    }

    @Test
    void AC_014_plansAllowedAgainAfterRestore() throws Exception {
        long horse = eligibleHorse();
        asJson(manager, post("/api/horses/" + horse + "/archive"), Map.of("reason", "OTHER")).andExpect(status().isOk());
        as(manager, post("/api/horses/" + horse + "/restore")).andExpect(status().isOk());
        asJson(trainer, post("/api/horses/" + horse + "/plans"), Map.of("phases", List.of(
                phase("Mới", today(), today().plusDays(5))))).andExpect(status().isCreated());
    }
}
