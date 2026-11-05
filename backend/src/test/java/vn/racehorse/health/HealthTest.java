package vn.racehorse.health;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Health status, locks and readiness (REQ-011, REQ-015, REQ-025). */
class HealthTest extends ApiTest {

    private static final ZoneId CLUB = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    JdbcTemplate jdbc;

    private AccountPrincipal manager;
    private AccountPrincipal vet;
    private AccountPrincipal owner;

    private long newHorse() throws Exception {
        manager = manager == null ? account(Role.CLUB_MANAGER) : manager;
        vet = vet == null ? account(Role.VETERINARIAN) : vet;
        owner = owner == null ? account(Role.HORSE_OWNER) : owner;
        return registerHorse(manager, owner, "MALE");
    }

    private void lift(long horse, String level) throws Exception {
        asJson(vet, post("/api/horses/" + horse + "/lock/lift"), Map.of("returnLevel", level)).andExpect(status().isOk());
    }

    private void setStatus(long horse, String to) throws Exception {
        asJson(vet, post("/api/horses/" + horse + "/health/status"), Map.of("status", to, "reason", "khám ngày " + to))
                .andExpect(status().isOk());
    }

    /** Intake lock lifted, status Đủ điều kiện, no lock. */
    private long eligibleHorse() throws Exception {
        long horse = newHorse();
        lift(horse, "RESUME_AS_IS");
        setStatus(horse, "ELIGIBLE");
        return horse;
    }

    private JsonNode health(long horse) throws Exception {
        return body(as(vet, get("/api/horses/" + horse + "/health")).andExpect(status().isOk()));
    }

    private String readiness(AccountPrincipal who, long horse) throws Exception {
        return body(as(who, get("/api/horses/" + horse + "/readiness")).andExpect(status().isOk()))
                .get("readiness").asText();
    }

    @Test
    void AC_002_newHorseIsUnderObservationWithSystemIntakeLock() throws Exception {
        JsonNode h = health(newHorse());
        assertThat(h.get("status").asText()).isEqualTo("UNDER_OBSERVATION");
        assertThat(h.get("lock").get("kind").asText()).isEqualTo("INTAKE");
        assertThat(h.get("lock").get("reason").asText()).isEqualTo("Chờ khám nhập đàn");
        assertThat(h.get("lock").get("placedBy").asText()).isEqualTo("Hệ thống");
        assertThat(h.get("newUnexamined").asBoolean()).isTrue();
    }

    @Test
    void oneActiveLockIsEnforcedByTheDatabase() throws Exception {
        long horse = newHorse();
        assertThatThrownBy(() -> jdbc.update(
                "insert into training_lock (horse_id, kind, reason, placed_at) values (?, 'MANUAL', 'x', now())", horse))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void AC_069_healthMapShowsStatusLockAndActivity() throws Exception {
        long eligible = eligibleHorse();
        long observed = newHorse();
        long injured = eligibleHorse();
        setStatus(injured, "INJURED");
        long quarantined = eligibleHorse();
        setStatus(quarantined, "QUARANTINED");

        JsonNode map = body(as(vet, get("/api/health")).andExpect(status().isOk()));
        Map<Long, JsonNode> byHorse = new HashMap<>();
        map.forEach(n -> byHorse.put(n.get("horseId").asLong(), n));
        assertThat(byHorse.get(eligible).get("status").asText()).isEqualTo("ELIGIBLE");
        assertThat(byHorse.get(eligible).get("lock").isNull()).isTrue();
        assertThat(byHorse.get(observed).get("status").asText()).isEqualTo("UNDER_OBSERVATION");
        assertThat(byHorse.get(observed).get("lock").get("kind").asText()).isEqualTo("INTAKE");
        assertThat(byHorse.get(injured).get("status").asText()).isEqualTo("INJURED");
        assertThat(byHorse.get(injured).get("lock").get("kind").asText()).isEqualTo("AUTOMATIC");
        assertThat(byHorse.get(quarantined).get("status").asText()).isEqualTo("QUARANTINED");
        for (long id : List.of(eligible, observed, injured, quarantined)) {
            assertThat(byHorse.get(id).get("activity").asText()).isEqualTo("IDLE");
        }
        as(owner, get("/api/health")).andExpect(status().isForbidden());
    }

    @Test
    void AC_071_injuredDirectlyToEligibleRefused() throws Exception {
        long horse = eligibleHorse();
        setStatus(horse, "INJURED");
        asJson(vet, post("/api/horses/" + horse + "/health/status"), Map.of("status", "ELIGIBLE", "reason", "khỏi"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        assertThat(health(horse).get("status").asText()).isEqualTo("INJURED");
    }

    @Test
    void AC_072_statusChangeWithoutReasonRefused() throws Exception {
        long horse = eligibleHorse();
        asJson(vet, post("/api/horses/" + horse + "/health/status"), Map.of("status", "UNDER_OBSERVATION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        assertThat(health(horse).get("status").asText()).isEqualTo("ELIGIBLE");
    }

    @Test
    void AC_075_headTrainerCannotChangeStatus() throws Exception {
        long horse = eligibleHorse();
        asJson(account(Role.HEAD_TRAINER), post("/api/horses/" + horse + "/health/status"),
                Map.of("status", "INJURED", "reason", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusHistoryKeptWithReasonAndAuthor() throws Exception {
        long horse = eligibleHorse();
        JsonNode history = health(horse).get("history");
        assertThat(history.get(0).get("to").asText()).isEqualTo("ELIGIBLE");
        assertThat(history.get(0).get("reason").asText()).isEqualTo("khám ngày ELIGIBLE");
        assertThat(history.get(0).get("changedBy").asText()).isEqualTo(vet.displayName());
        assertThat(history.get(history.size() - 1).get("to").asText()).isEqualTo("UNDER_OBSERVATION");
    }

    @Test
    void AC_074_manualLockThenInjuredStillOneLock() throws Exception {
        long horse = eligibleHorse();
        setStatus(horse, "UNDER_OBSERVATION");
        asJson(vet, post("/api/horses/" + horse + "/lock"), Map.of("reason", "theo dõi chân")).andExpect(status().isOk());
        setStatus(horse, "INJURED");
        Integer active = jdbc.queryForObject(
                "select count(*) from training_lock where horse_id = ? and lifted_at is null", Integer.class, horse);
        assertThat(active).isEqualTo(1);
        assertThat(health(horse).get("lock").get("kind").asText()).isEqualTo("MANUAL");
    }

    @Test
    void AC_080_manualLockWithReasonAuthorTimeAndReviewDate() throws Exception {
        long horse = eligibleHorse();
        setStatus(horse, "UNDER_OBSERVATION");
        String review = LocalDate.now(CLUB).plusDays(7).toString();
        JsonNode lock = body(asJson(vet, post("/api/horses/" + horse + "/lock"),
                Map.of("reason", "đau nhẹ chân trái", "reviewDate", review)).andExpect(status().isOk())).get("lock");
        assertThat(lock.get("kind").asText()).isEqualTo("MANUAL");
        assertThat(lock.get("reason").asText()).isEqualTo("đau nhẹ chân trái");
        assertThat(lock.get("placedBy").asText()).isEqualTo(vet.displayName());
        assertThat(lock.get("placedAt").asText()).isNotBlank();
        assertThat(lock.get("reviewDate").asText()).isEqualTo(review);
    }

    @Test
    void theAssignedHeadTrainerIsNotifiedWhenALockIsPlaced() throws Exception {
        long horse = eligibleHorse();
        setStatus(horse, "UNDER_OBSERVATION");
        AccountPrincipal trainer = defaultTrainer();
        AccountPrincipal otherTrainer = account(Role.HEAD_TRAINER);
        AccountPrincipal groom = account(Role.GROOM);
        long unreadBefore = body(as(trainer, get("/api/notifications"))).get("unread").asLong();
        asJson(vet, post("/api/horses/" + horse + "/lock"), Map.of("reason", "kiểm tra móng")).andExpect(status().isOk());

        JsonNode inbox = body(as(trainer, get("/api/notifications")));
        assertThat(inbox.get("unread").asLong()).isEqualTo(unreadBefore + 1);
        assertThat(inbox.get("items").get(0).get("message").asText()).contains("kiểm tra móng");
        // Another Head Trainer is not told about a horse that is not theirs (BR-157, AC-295).
        assertThat(body(as(otherTrainer, get("/api/notifications"))).get("items").size()).isZero();
        assertThat(body(as(groom, get("/api/notifications"))).get("items").size()).isZero();

        long id = inbox.get("items").get(0).get("id").asLong();
        as(groom, post("/api/notifications/" + id + "/read")).andExpect(status().isForbidden());
        as(trainer, post("/api/notifications/" + id + "/read")).andExpect(status().isOk());
        assertThat(body(as(trainer, get("/api/notifications"))).get("unread").asLong()).isEqualTo(unreadBefore);
    }

    @Test
    void AC_083_liftRefusedWhileInjured() throws Exception {
        long horse = eligibleHorse();
        setStatus(horse, "INJURED");
        asJson(vet, post("/api/horses/" + horse + "/lock/lift"), Map.of("returnLevel", "RESUME_AS_IS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIFT_NOT_ALLOWED"));
        assertThat(health(horse).get("lock").isNull()).isFalse();
    }

    @Test
    void AC_084_lockStaysWhenInjuredHorseReturnsToObservation() throws Exception {
        long horse = eligibleHorse();
        setStatus(horse, "INJURED");
        setStatus(horse, "UNDER_OBSERVATION");
        JsonNode h = health(horse);
        assertThat(h.get("status").asText()).isEqualTo("UNDER_OBSERVATION");
        assertThat(h.get("lock").get("kind").asText()).isEqualTo("AUTOMATIC");
    }

    @Test
    void AC_085_lockStillActiveAfterItsReviewDate() throws Exception {
        long horse = eligibleHorse();
        setStatus(horse, "UNDER_OBSERVATION");
        String yesterday = LocalDate.now(CLUB).minusDays(1).toString();
        asJson(vet, post("/api/horses/" + horse + "/lock"), Map.of("reason", "xem lại", "reviewDate", yesterday))
                .andExpect(status().isOk());
        JsonNode lock = health(horse).get("lock");
        assertThat(lock.isNull()).isFalse();
        assertThat(lock.get("reviewDate").asText()).isEqualTo(yesterday);
    }

    @Test
    void AC_087_reviewNoteSeenByVeterinarianLockUnchanged() throws Exception {
        long horse = newHorse();
        long lockId = health(horse).get("lock").get("id").asLong();
        AccountPrincipal trainer = defaultTrainer();
        asJson(trainer, post("/api/horses/" + horse + "/lock/review-notes"), Map.of("note", "ngựa đã ổn, xin xem lại"))
                .andExpect(status().isOk());
        JsonNode lock = health(horse).get("lock");
        assertThat(lock.get("id").asLong()).isEqualTo(lockId);
        assertThat(lock.get("reviewNotes").get(0).get("note").asText()).isEqualTo("ngựa đã ổn, xin xem lại");
        assertThat(lock.get("reviewNotes").get(0).get("author").asText()).isEqualTo(trainer.displayName());
    }

    @Test
    void AC_088_headTrainerCannotLift() throws Exception {
        long horse = newHorse();
        asJson(account(Role.HEAD_TRAINER), post("/api/horses/" + horse + "/lock/lift"),
                Map.of("returnLevel", "RESUME_AS_IS"))
                .andExpect(status().isForbidden());
        assertThat(health(horse).get("lock").isNull()).isFalse();
    }

    @Test
    void AC_119_manualLockOnEligibleHorseRefused() throws Exception {
        long horse = eligibleHorse();
        asJson(vet, post("/api/horses/" + horse + "/lock"), Map.of("reason", "phòng ngừa"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCK_REQUIRES_OBSERVATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cần theo dõi")));
        assertThat(health(horse).get("lock").isNull()).isTrue();
    }

    @Test
    void AC_025_auditFilterShowsYesterdaysLockWithReason() throws Exception {
        long horse = eligibleHorse();
        setStatus(horse, "UNDER_OBSERVATION");
        TestClock.shift(Duration.ofDays(-1));
        asJson(vet, post("/api/horses/" + horse + "/lock"), Map.of("reason", "sưng khớp gối")).andExpect(status().isOk());
        TestClock.reset();

        String yesterday = LocalDate.now(CLUB).minusDays(1).toString();
        JsonNode log = body(as(manager, get("/api/audit").param("actorId", vet.id().toString())
                .param("from", yesterday).param("to", yesterday)).andExpect(status().isOk()));
        List<JsonNode> locks = StreamSupport.stream(log.spliterator(), false)
                .filter(e -> e.get("action").asText().equals("LOCK_PLACED") && e.get("targetId").asLong() == horse)
                .toList();
        assertThat(locks).hasSize(1);
        assertThat(json.readTree(locks.get(0).get("details").asText()).get("reason").asText()).isEqualTo("sưng khớp gối");
    }

    // ---- readiness (BR-080) --------------------------------------------------------------------------------

    @Test
    void AC_110_eligibleWithoutLockIsReady() throws Exception {
        long horse = eligibleHorse();
        assertThat(readiness(owner, horse)).isEqualTo("READY");
    }

    @Test
    void AC_111_readinessReflectsCurrentState() throws Exception {
        long horse = eligibleHorse();
        assertThat(readiness(owner, horse)).isEqualTo("READY");
        setStatus(horse, "INJURED");
        assertThat(readiness(owner, horse)).isEqualTo("NOT_READY");
    }

    @Test
    void AC_112_otherOwnersHorseRefused() throws Exception {
        long horse = eligibleHorse();
        as(account(Role.HORSE_OWNER), get("/api/horses/" + horse + "/readiness")).andExpect(status().isForbidden());
    }

    @Test
    void AC_121_underObservationWithoutLock() throws Exception {
        long horse = newHorse();
        lift(horse, "RESUME_WITH_ADJUSTMENT");
        assertThat(readiness(owner, horse)).isEqualTo("UNDER_OBSERVATION");
    }

    @Test
    void AC_122_newHorseNotReady() throws Exception {
        long horse = newHorse();
        assertThat(readiness(owner, horse)).isEqualTo("NOT_READY");
    }

    @Test
    void AC_123_injuredOrQuarantinedNotReady() throws Exception {
        long injured = eligibleHorse();
        setStatus(injured, "INJURED");
        long quarantined = eligibleHorse();
        setStatus(quarantined, "QUARANTINED");
        assertThat(readiness(owner, injured)).isEqualTo("NOT_READY");
        assertThat(readiness(owner, quarantined)).isEqualTo("NOT_READY");
    }

    @Test
    void AC_124_liftChangesReadinessToUnderObservation() throws Exception {
        long horse = newHorse();
        assertThat(readiness(owner, horse)).isEqualTo("NOT_READY");
        lift(horse, "RESUME_AS_IS");
        assertThat(readiness(owner, horse)).isEqualTo("UNDER_OBSERVATION");
    }
}
