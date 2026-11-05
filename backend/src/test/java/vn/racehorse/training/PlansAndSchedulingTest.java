package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.TestClock;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Plans (BR-030, BR-031) and scheduling (BR-032 to BR-041). */
class PlansAndSchedulingTest extends TrainingTestBase {

    @Test
    void AC_028_planWithTwoPhasesActivatedInOrder() throws Exception {
        long horse = eligibleHorse();
        JsonNode plan = body(asJson(trainer, post("/api/horses/" + horse + "/plans"), Map.of("phases", List.of(
                phase("Nền tảng", today(), today().plusDays(20)),
                phase("Tăng tốc", today().plusDays(21), today().plusDays(40)))))
                .andExpect(status().isCreated()));
        long id = plan.get("id").asLong();
        assertThat(plan.get("state").asText()).isEqualTo("DRAFT");
        JsonNode active = body(as(trainer, post("/api/plans/" + id + "/activate")).andExpect(status().isOk()));
        assertThat(active.get("state").asText()).isEqualTo("ACTIVE");
        assertThat(active.get("phases").get(0).get("name").asText()).isEqualTo("Nền tảng");
        assertThat(active.get("phases").get(1).get("name").asText()).isEqualTo("Tăng tốc");
        assertThat(active.get("phases").get(1).get("weeklyVolumeKm").asDouble()).isEqualTo(12.5);
    }

    @Test
    void AC_029_draftPlanForNewLockedUnexaminedHorseWithLabel() throws Exception {
        long horse = newHorse();
        long plan = draftPlan(horse);
        as(trainer, get("/api/plans/" + plan)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DRAFT"));
        assertThat(body(as(trainer, get("/api/horses/" + horse + "/health"))).get("newUnexamined").asBoolean()).isTrue();
        JsonNode board = body(as(trainer, get("/api/progress")));
        JsonNode row = null;
        for (JsonNode r : board) {
            if (r.get("horseId").asLong() == horse) row = r;
        }
        assertThat(row).isNotNull();
        assertThat(row.get("newUnexamined").asBoolean()).isTrue();
    }

    @Test
    void AC_030_completionSuggestedButPlanStaysActive() throws Exception {
        long horse = eligibleHorse();
        long plan = body(asJson(trainer, post("/api/horses/" + horse + "/plans"), Map.of("phases", List.of(
                phase("Đã xong", today().minusDays(10), today().minusDays(1)))))
                .andExpect(status().isCreated())).get("id").asLong();
        as(trainer, post("/api/plans/" + plan + "/activate")).andExpect(status().isOk());

        as(trainer, get("/api/plans/" + plan))
                .andExpect(jsonPath("$.completionSuggested").value(true))
                .andExpect(jsonPath("$.state").value("ACTIVE"));
        as(trainer, post("/api/plans/" + plan + "/complete")).andExpect(jsonPath("$.state").value("COMPLETED"));
    }

    @Test
    void AC_031_noPlanForArchivedHorse() throws Exception {
        long horse = eligibleHorse();
        asJson(manager, post("/api/horses/" + horse + "/archive"), Map.of("reason", "RETIRED")).andExpect(status().isOk());
        asJson(trainer, post("/api/horses/" + horse + "/plans"), Map.of("phases", List.of(
                phase("X", today(), today().plusDays(5)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
    }

    @Test
    void AC_032_veterinarianCannotCreatePlan() throws Exception {
        long horse = eligibleHorse();
        asJson(vet, post("/api/horses/" + horse + "/plans"), Map.of("phases", List.of()))
                .andExpect(status().isForbidden());
    }

    @Test
    void AC_033_sessionScheduledWithValues() throws Exception {
        long plan = activePlan(eligibleHorse());
        JsonNode s = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"),
                sessionBody(today().plusDays(1), "TROT", false)).andExpect(status().isCreated()));
        assertThat(s.get("state").asText()).isEqualTo("SCHEDULED");
        assertThat(s.get("date").asText()).isEqualTo(today().plusDays(1).toString());
        assertThat(s.get("type").asText()).isEqualTo("TROT");
        assertThat(s.get("plannedDurationMin").asInt()).isEqualTo(60);
        assertThat(s.get("responsiblePerson").get("id").asLong()).isEqualTo(defaultGroom().id());
    }

    @Test
    void AC_034_noSchedulingInDraftPlan() throws Exception {
        long plan = draftPlan(eligibleHorse());
        long drafted = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"),
                sessionBody(today().plusDays(2), "GALLOP", true)).andExpect(status().isCreated())).get("id").asLong();
        assertThat(state(drafted)).isEqualTo("DRAFT");
        as(trainer, post("/api/sessions/" + drafted + "/schedule"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_ACTIVE"));
        as(trainer, post("/api/plans/" + plan + "/activate")).andExpect(status().isOk());
        as(trainer, post("/api/sessions/" + drafted + "/schedule")).andExpect(jsonPath("$.state").value("SCHEDULED"));
    }

    @Test
    void AC_035_noSchedulingWhileLockedReasonShown() throws Exception {
        long horse = observedHorse();
        long plan = activePlan(horse);
        lock(horse, "đau lưng");
        asJson(trainer, post("/api/plans/" + plan + "/sessions"), sessionBody(today().plusDays(1), "GALLOP", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_LOCKED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("đau lưng")));
    }

    @Test
    void AC_036_missedAfterEndOfClubDay() throws Exception {
        long plan = activePlan(eligibleHorse());
        long session = schedule(plan, today());
        sessionService.markMissed();
        assertThat(state(session)).isEqualTo("SCHEDULED");
        TestClock.shift(Duration.ofDays(1));
        sessionService.markMissed();
        TestClock.reset();
        assertThat(state(session)).isEqualTo("MISSED");
    }

    @Test
    void AC_037_cancelledWithReasonStaysInHistory() throws Exception {
        long plan = activePlan(eligibleHorse());
        long session = schedule(plan, today().plusDays(3));
        asJson(trainer, post("/api/sessions/" + session + "/cancel"), Map.of("reason", "trời mưa lớn"))
                .andExpect(jsonPath("$.state").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("trời mưa lớn"));
        JsonNode all = body(as(trainer, get("/api/plans/" + plan + "/sessions")));
        assertThat(all.findValues("id").stream().map(JsonNode::asLong)).contains(session);
    }

    @Test
    void AC_038_ownerCannotAddOrChangeSession() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long session = schedule(plan, today().plusDays(1));
        asJson(owner, post("/api/plans/" + plan + "/sessions"), sessionBody(today().plusDays(2), "TROT", false))
                .andExpect(status().isForbidden());
        asJson(owner, post("/api/sessions/" + session + "/cancel"), Map.of("reason", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void AC_052_trialRunScheduledAndShownAsSuch() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long trial = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"),
                sessionBody(today().plusDays(4), "TRIAL_RUN", false)).andExpect(status().isCreated())).get("id").asLong();
        JsonNode schedule = body(as(owner, get("/api/horses/" + horse + "/schedule")));
        JsonNode item = null;
        for (JsonNode n : schedule) {
            if (n.get("id").asLong() == trial) item = n;
        }
        assertThat(item).isNotNull();
        assertThat(item.get("type").asText()).isEqualTo("TRIAL_RUN");
    }

    @Test
    void AC_053_trialRunRefusedWhileLocked() throws Exception {
        long horse = observedHorse();
        long plan = activePlan(horse);
        long trial = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"),
                sessionBody(today(), "TRIAL_RUN", false)).andExpect(status().isCreated())).get("id").asLong();
        lock(horse, "kiểm tra tim");
        asJson(trainer, post("/api/plans/" + plan + "/sessions"), sessionBody(today().plusDays(1), "TRIAL_RUN", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("kiểm tra tim")));
        asJson(trainer, post("/api/sessions/" + trial + "/start"), Map.of("acknowledgeWarning", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_LOCKED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("kiểm tra tim")));
    }

    @Test
    void AC_054_groomSeesTrialRunsOnlyAsAssignedSessions() throws Exception {
        long plan = activePlan(eligibleHorse());
        var groom = account(Role.GROOM);
        Map<String, Object> trial = sessionBody(today().plusDays(1), "TRIAL_RUN", false);
        trial.put("responsiblePersonId", groom.id());
        long assigned = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), trial)
                .andExpect(status().isCreated())).get("id").asLong();

        // Plan and session screens and scheduling are refused, trial runs included (DEC-042).
        as(groom, get("/api/plans/" + plan)).andExpect(status().isForbidden());
        as(groom, get("/api/plans/" + plan + "/sessions")).andExpect(status().isForbidden());
        as(groom, get("/api/sessions/" + assigned)).andExpect(status().isForbidden());
        asJson(groom, post("/api/plans/" + plan + "/sessions"), sessionBody(today().plusDays(1), "TRIAL_RUN", false))
                .andExpect(status().isForbidden());
        // The trial run they are responsible for is listed, read-only, in "Buổi tập được giao".
        var mine = body(as(groom, get("/api/my-sessions")).andExpect(status().isOk()));
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("id").asLong()).isEqualTo(assigned);
        assertThat(mine.get(0).get("type").asText()).isEqualTo("TRIAL_RUN");
    }
}
