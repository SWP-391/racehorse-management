package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Planned start time, ordering, overlap rules and the late-change threshold (BR-120 to BR-123, DEC-043 to DEC-045). */
class SessionStartTimeTest extends TrainingTestBase {

    @Autowired
    JdbcTemplate jdbc;

    /** The database is shared by all tests: put the threshold back to its default (BR-123). */
    @AfterEach
    void resetThreshold() {
        jdbc.update("update club_setting set value = '12' where key = 'late_change_threshold_hours'");
    }

    private LocalDateTime clubNow() {
        return Instant.now().atZone(CLUB).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);
    }

    private Map<String, Object> at(LocalDate date, LocalTime time, AccountPrincipal responsible, Boolean ack) {
        Map<String, Object> s = sessionBody(date, "GALLOP", false);
        if (time == null) {
            s.remove("startTime");
        } else {
            s.put("startTime", time.toString());
        }
        s.put("responsiblePersonId", responsible.id());
        if (ack != null) {
            s.put("acknowledgeWarning", ack);
        }
        return s;
    }

    private ResultActions create(long plan, LocalDate date, LocalTime time, AccountPrincipal responsible, Boolean ack)
            throws Exception {
        return asJson(trainer, post("/api/plans/" + plan + "/sessions"), at(date, time, responsible, ack));
    }

    private long created(long plan, LocalDate date, LocalTime time, AccountPrincipal responsible) throws Exception {
        return body(create(plan, date, time, responsible, null).andExpect(status().isCreated())).get("id").asLong();
    }

    private long created(long plan, LocalDateTime start, AccountPrincipal responsible) throws Exception {
        return created(plan, start.toLocalDate(), start.toLocalTime(), responsible);
    }

    /** A session as it was before start times existed (DEC-043). */
    private void dropStartTime(long session) {
        jdbc.update("update training_session set planned_start_time = null where id = ?", session);
    }

    private List<Long> ids(JsonNode list) {
        List<Long> out = new ArrayList<>();
        list.forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    private Map<String, Object> change(AccountPrincipal to, Boolean ack) {
        Map<String, Object> b = new HashMap<>();
        b.put("responsiblePersonId", to.id());
        if (ack != null) {
            b.put("acknowledgeWarning", ack);
        }
        return b;
    }

    private long responsible(long session) throws Exception {
        return session(session).get("responsiblePerson").get("id").asLong();
    }

    private List<JsonNode> audit(AccountPrincipal actor, String action) throws Exception {
        JsonNode all = body(as(manager, get("/api/audit").param("actorId", actor.id().toString())));
        return StreamSupport.stream(all.spliterator(), false).filter(e -> e.get("action").asText().equals(action))
                .toList();
    }

    @Test
    void AC_194_sessionSavedWithItsStartTime() throws Exception {
        long plan = activePlan(eligibleHorse());
        LocalDate day = today().plusDays(2);
        long id = created(plan, day, LocalTime.of(7, 0), defaultGroom());
        assertThat(session(id).get("startTime").asText()).startsWith("07:00");
        JsonNode list = body(as(trainer, get("/api/plans/" + plan + "/sessions")));
        assertThat(list.get(0).get("startTime").asText()).startsWith("07:00");
        assertThat(list.get(0).get("plannedDurationMin").asInt()).isEqualTo(60);
    }

    @Test
    void AC_195_orderedByDateThenStartTimeForEveryRole() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        LocalDate day = today().plusDays(3);
        long nine = created(plan, day, LocalTime.of(9, 0), g);
        long sixThirty = created(plan, day, LocalTime.of(6, 30), g);
        long untimed = created(plan, day, LocalTime.of(12, 0), g);
        dropStartTime(untimed);
        List<Long> expected = List.of(untimed, sixThirty, nine);

        assertThat(ids(body(as(trainer, get("/api/plans/" + plan + "/sessions"))))).containsExactlyElementsOf(expected);
        assertThat(ids(body(as(owner, get("/api/horses/" + horse + "/schedule"))))).containsExactlyElementsOf(expected);
        assertThat(ids(body(as(g, get("/api/my-sessions"))))).containsExactlyElementsOf(expected);
    }

    @Test
    void AC_196_startTimeIsMandatory() throws Exception {
        long plan = activePlan(eligibleHorse());
        create(plan, today().plusDays(1), null, defaultGroom(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.startTime").value("Bắt buộc nhập giờ bắt đầu."));
        assertThat(body(as(trainer, get("/api/plans/" + plan + "/sessions"))).size()).isZero();
    }

    @Test
    void AC_197_overlapRefusedNamingTheOtherSession() throws Exception {
        long plan = activePlan(eligibleHorse());
        LocalDate day = today().plusDays(2);
        created(plan, day, LocalTime.of(7, 0), defaultGroom());
        create(plan, day, LocalTime.of(7, 30), defaultGroom(), true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_OVERLAP"))
                .andExpect(jsonPath("$.message").value(containsString("lúc 07:00")));
        assertThat(body(as(trainer, get("/api/plans/" + plan + "/sessions"))).size()).isEqualTo(1);
    }

    @Test
    void AC_198_adjacentAndCancelledSessionsDoNotCount() throws Exception {
        long plan = activePlan(eligibleHorse());
        LocalDate day = today().plusDays(2);
        created(plan, day, LocalTime.of(7, 0), defaultGroom());
        long cancelled = created(plan, day, LocalTime.of(9, 0), defaultGroom());
        asJson(trainer, post("/api/sessions/" + cancelled + "/cancel"), Map.of("reason", "đổi lịch"))
                .andExpect(status().isOk());

        create(plan, day, LocalTime.of(8, 0), defaultGroom(), null).andExpect(status().isCreated());
        create(plan, day, LocalTime.of(9, 0), defaultGroom(), null).andExpect(status().isCreated());
    }

    @Test
    void AC_199_theStartTimeNeverStartsASession() throws Exception {
        LocalDateTime now = clubNow();
        LocalTime passed = now.toLocalTime().isBefore(LocalTime.of(1, 0)) ? LocalTime.MIDNIGHT
                : now.toLocalTime().minusHours(1);
        long id = created(activePlan(eligibleHorse()), today(), passed, defaultGroom());

        sessionService.flagOvertime();
        sessionService.autoClose();
        sessionService.markMissed();

        assertThat(state(id)).isEqualTo("SCHEDULED");
        assertThat(session(id).get("startedAt").isNull()).isTrue();
    }

    @Test
    void AC_200_overlappingAssignmentIsRefused() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        long horseA = eligibleHorse();
        LocalDate day = today().plusDays(2);
        created(activePlan(horseA), day, LocalTime.of(7, 0), g);
        long planB = activePlan(eligibleHorse());

        // BR-161: another session of the same Groom at an overlapping time is refused, never confirmed.
        for (Boolean ack : new Boolean[] {false, true}) {
            create(planB, day, LocalTime.of(7, 30), g, ack)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GROOM_NOT_AVAILABLE"))
                    .andExpect(jsonPath("$.message").value(containsString(horseName(horseA))))
                    .andExpect(jsonPath("$.message").value(containsString("lúc 07:00")));
        }
        assertThat(body(as(trainer, get("/api/plans/" + planB + "/sessions"))).size()).isZero();

        // Adjacent in time is fine.
        long b = body(create(planB, day, LocalTime.of(8, 0), g, null).andExpect(status().isCreated()))
                .get("id").asLong();
        assertThat(responsible(b)).isEqualTo(g.id());
        assertThat(body(as(g, get("/api/my-sessions"))).size()).isEqualTo(2);

        // The same refusal when the person is given an overlapping session by a change.
        long c = created(activePlan(eligibleHorse()), day, LocalTime.of(7, 15), defaultGroom());
        for (Boolean ack : new Boolean[] {false, true}) {
            asJson(trainer, put("/api/sessions/" + c + "/responsible"), change(g, ack))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GROOM_NOT_AVAILABLE"))
                    .andExpect(jsonPath("$.message").value(containsString(horseName(horseA))));
        }
        assertThat(responsible(c)).isEqualTo(defaultGroom().id());
    }

    @Test
    void AC_201_shiftIntoAnOverlapRefused() throws Exception {
        long horse = observedHorse();
        long plan = activePlan(horse);
        LocalDate day = today().plusDays(3);
        long reviewed = created(plan, day, LocalTime.of(7, 0), defaultGroom());
        lock(horse, "nghỉ hồi phục");
        asJson(vet, post("/api/horses/" + horse + "/lock/lift"), Map.of("returnLevel", "RESUME_WITH_ADJUSTMENT"))
                .andExpect(status().isOk());
        assertThat(state(reviewed)).isEqualTo("AWAITING_REVIEW");
        // Another plan of the same horse already has a session the next day at 07:30.
        long other = draftPlan(horse);
        Map<String, Object> drafted = at(day.plusDays(1), LocalTime.of(7, 30), defaultGroom(), null);
        drafted.put("draft", true);
        asJson(trainer, post("/api/plans/" + other + "/sessions"), drafted).andExpect(status().isCreated());

        for (boolean dryRun : List.of(true, false)) {
            asJson(trainer, post("/api/plans/" + plan + "/shift"), Map.of("days", 1, "dryRun", dryRun))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SESSION_OVERLAP"))
                    .andExpect(jsonPath("$.message").value(containsString("lúc 07:30")));
        }
        assertThat(state(reviewed)).isEqualTo("AWAITING_REVIEW");
        assertThat(session(reviewed).get("date").asText()).isEqualTo(day.toString());
    }

    @Test
    void AC_202_onlyTheHeadTrainerSchedules() throws Exception {
        long plan = activePlan(eligibleHorse());
        AccountPrincipal groom = account(Role.GROOM);
        for (AccountPrincipal who : List.of(vet, groom, owner, manager)) {
            asJson(who, post("/api/plans/" + plan + "/sessions"), at(today().plusDays(2), LocalTime.of(7, 0), trainer, true))
                    .andExpect(status().isForbidden());
        }
        assertThat(body(as(trainer, get("/api/plans/" + plan + "/sessions"))).size()).isZero();
    }

    @Test
    void AC_203_thresholdChangedAuditedAndApplied() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        LocalDateTime inEightHours = clubNow().plusHours(8);
        long first = created(activePlan(eligibleHorse()), inEightHours, g);
        // With the default 12 hours, 8 hours ahead is late.
        asJson(trainer, put("/api/sessions/" + first + "/responsible"), change(h, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("không kịp chuẩn bị")));

        asJson(manager, put("/api/settings/late-change-threshold"), Map.of("hours", 6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(6));
        as(manager, get("/api/settings/late-change-threshold")).andExpect(jsonPath("$.hours").value(6));
        assertThat(audit(manager, "SETTING_CHANGED").stream().map(e -> e.get("details").asText()).toList())
                .anyMatch(d -> d.contains("late_change_threshold_hours") && d.contains("\"from\":12")
                        && d.contains("\"to\":6"));

        asJson(trainer, put("/api/sessions/" + first + "/responsible"), change(h, false)).andExpect(status().isOk());
        assertThat(responsible(first)).isEqualTo(h.id());
    }

    @Test
    void AC_204_sessionWithoutStartTimeKeepsTheDateRule() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        long tomorrow = created(activePlan(eligibleHorse()), today().plusDays(1), LocalTime.of(5, 0), g);
        dropStartTime(tomorrow);
        asJson(trainer, put("/api/sessions/" + tomorrow + "/responsible"), change(h, false)).andExpect(status().isOk());

        long sameDay = created(activePlan(eligibleHorse()), today(), LocalTime.of(23, 30), g);
        dropStartTime(sameDay);
        asJson(trainer, put("/api/sessions/" + sameDay + "/responsible"), change(h, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WARNING_CONFIRMATION_REQUIRED"));
        assertThat(responsible(sameDay)).isEqualTo(g.id());
    }

    @Test
    void AC_205_invalidThresholdRefused() throws Exception {
        for (Object bad : List.of(0, -3, 1.5)) {
            asJson(manager, put("/api/settings/late-change-threshold"), Map.of("hours", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_LATE_CHANGE_THRESHOLD"));
        }
        as(manager, get("/api/settings/late-change-threshold")).andExpect(jsonPath("$.hours").value(12));
    }

    @Test
    void AC_206_onlyTheClubManagerSetsTheThreshold() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);
        for (AccountPrincipal who : List.of(trainer, vet, groom, owner)) {
            asJson(who, put("/api/settings/late-change-threshold"), Map.of("hours", 3))
                    .andExpect(status().isForbidden());
        }
        as(manager, get("/api/settings/late-change-threshold")).andExpect(jsonPath("$.hours").value(12));
    }
}
