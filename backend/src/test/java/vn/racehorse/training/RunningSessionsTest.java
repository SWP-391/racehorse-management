package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.racehorse.common.TestClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Starting, ending, overtime and auto-close (BR-034 to BR-038, BR-094). */
class RunningSessionsTest extends TrainingTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void AC_039_startAndCompleteHorseIdleAgain() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        assertThat(health(horse).get("activity").asText()).isEqualTo("TRAINING");
        end(session);
        JsonNode s = session(session);
        assertThat(s.get("state").asText()).isEqualTo("COMPLETED");
        assertThat(s.get("form").asInt()).isEqualTo(4);
        assertThat(s.get("intensity").asText()).isEqualTo("MEDIUM");
        assertThat(health(horse).get("activity").asText()).isEqualTo("IDLE");
    }

    @Test
    void AC_040_warningForUnderObservationStartsAfterConfirmation() throws Exception {
        long session = schedule(activePlan(observedHorse()), today());
        asJson(trainer, post("/api/sessions/" + session + "/start"), Map.of("acknowledgeWarning", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WARNING_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cần theo dõi")));
        assertThat(state(session)).isEqualTo("SCHEDULED");
        start(session, true);
        assertThat(state(session)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void AC_041_startRefusedWhileInjuredWithLockReason() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        setStatus(horse, "INJURED");
        asJson(trainer, post("/api/sessions/" + session + "/start"), Map.of("acknowledgeWarning", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_LOCKED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Chấn thương")));
    }

    @Test
    void AC_042_secondSessionRefused() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long first = schedule(plan, today());
        long second = schedule(plan, today());
        start(first, false);
        asJson(trainer, post("/api/sessions/" + second + "/start"), Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_ALREADY_TRAINING"));
    }

    @Test
    void AC_043_missingFormRefusedAndNamed() throws Exception {
        long session = schedule(activePlan(eligibleHorse()), today());
        start(session, false);
        Map<String, Object> noForm = indicators();
        noForm.remove("form");
        asJson(trainer, post("/api/sessions/" + session + "/end"), noForm)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INDICATORS_INCOMPLETE"))
                .andExpect(jsonPath("$.fields.form").exists());
        assertThat(state(session)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void AC_044_earlyEndWithReasonRecorded() throws Exception {
        long session = schedule(activePlan(eligibleHorse()), today());
        start(session, false);
        Map<String, Object> body = indicators();
        body.put("earlyEndReason", "ngựa có dấu hiệu mệt");
        asJson(trainer, post("/api/sessions/" + session + "/end"), body).andExpect(status().isOk());
        JsonNode s = session(session);
        assertThat(s.get("state").asText()).isEqualTo("COMPLETED");
        assertThat(s.get("earlyEndReason").asText()).isEqualTo("ngựa có dấu hiệu mệt");
    }

    @Test
    void AC_045_lockRecordedFirstMeansStartRefused() throws Exception {
        long horse = observedHorse();
        long session = schedule(activePlan(horse), today());
        lock(horse, "nghi viêm gân");
        asJson(trainer, post("/api/sessions/" + session + "/start"), Map.of("acknowledgeWarning", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_LOCKED"));
        assertThat(health(horse).get("lock").isNull()).isFalse();
    }

    /** AC-045: simultaneous lock and start never leave a lock and a running session together (TECH-026). */
    @Test
    void AC_045_concurrentLockAndStartNeverCoexist() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 6; round++) {
                long horse = observedHorse();
                long session = schedule(activePlan(horse), today());
                CountDownLatch go = new CountDownLatch(1);
                Future<Integer> lockCall = pool.submit(() -> {
                    go.await();
                    return asJson(vet, post("/api/horses/" + horse + "/lock"), Map.of("reason", "đồng thời"))
                            .andReturn().getResponse().getStatus();
                });
                Future<Integer> startCall = pool.submit(() -> {
                    go.await();
                    return asJson(trainer, post("/api/sessions/" + session + "/start"),
                            Map.of("acknowledgeWarning", true)).andReturn().getResponse().getStatus();
                });
                go.countDown();
                int lockStatus = lockCall.get(30, TimeUnit.SECONDS);
                int startStatus = startCall.get(30, TimeUnit.SECONDS);

                boolean locked = !health(horse).get("lock").isNull();
                boolean running = state(session).equals("IN_PROGRESS");
                assertThat(locked && running).as("round %d: lock and running session coexist", round).isFalse();
                assertThat(locked || running).as("round %d: one of them must win", round).isTrue();
                assertThat(List.of(lockStatus, startStatus)).contains(200).contains(409);
                if (running) {
                    end(session);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void AC_046_veterinarianCannotStart() throws Exception {
        long session = schedule(activePlan(eligibleHorse()), today());
        asJson(vet, post("/api/sessions/" + session + "/start"), Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void AC_061_formAndCommentStored() throws Exception {
        long session = schedule(activePlan(eligibleHorse()), today());
        start(session, false);
        Map<String, Object> body = indicators();
        body.put("form", 4);
        body.put("comment", "Vào cua tốt, cần tăng sức bền");
        asJson(trainer, post("/api/sessions/" + session + "/end"), body).andExpect(status().isOk());
        JsonNode s = session(session);
        assertThat(s.get("form").asInt()).isEqualTo(4);
        assertThat(s.get("comment").asText()).isEqualTo("Vào cua tốt, cần tăng sức bền");
    }

    @Test
    void AC_062_formSixRefused() throws Exception {
        long session = schedule(activePlan(eligibleHorse()), today());
        start(session, false);
        Map<String, Object> body = indicators();
        body.put("form", 6);
        asJson(trainer, post("/api/sessions/" + session + "/end"), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.form").value("Phong độ chấm từ 1 đến 5."));
    }

    @Test
    void AC_063_emptyCommentAccepted() throws Exception {
        long session = schedule(activePlan(eligibleHorse()), today());
        start(session, false);
        Map<String, Object> body = indicators();
        body.put("comment", "");
        asJson(trainer, post("/api/sessions/" + session + "/end"), body).andExpect(status().isOk());
        assertThat(state(session)).isEqualTo("COMPLETED");
    }

    @Test
    void AC_064_clubManagerCannotRateForm() throws Exception {
        long session = schedule(activePlan(eligibleHorse()), today());
        start(session, false);
        asJson(manager, post("/api/sessions/" + session + "/end"), indicators()).andExpect(status().isForbidden());
    }

    // ---- overtime and auto-close ------------------------------------------------------------------------

    @Test
    void AC_047_overtimeFlaggedAndHeadTrainerNotified() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        TestClock.shift(Duration.ofMinutes(59));
        sessionService.flagOvertime();
        assertThat(session(session).get("overtime").asBoolean()).isFalse();
        TestClock.shift(Duration.ofMinutes(60));
        sessionService.flagOvertime();
        TestClock.reset();
        assertThat(session(session).get("overtime").asBoolean()).isTrue();
        assertThat(state(session)).isEqualTo("IN_PROGRESS");
        assertThat(hasNotification(trainer, "\"" + horseName(horse) + "\" đã quá giờ")).isTrue();
    }

    @Test
    void AC_048_autoClosedAfterOneHourGrace() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        TestClock.shift(Duration.ofMinutes(119));
        sessionService.autoClose();
        assertThat(state(session)).isEqualTo("IN_PROGRESS");
        TestClock.shift(Duration.ofMinutes(120));
        sessionService.autoClose();
        TestClock.reset();
        JsonNode s = session(session);
        assertThat(s.get("state").asText()).isEqualTo("AUTO_CLOSED");
        assertThat(s.get("endTimeAutomatic").asBoolean()).isTrue();
        assertThat(s.get("missingIndicators").asBoolean()).isTrue();
        assertThat(health(horse).get("activity").asText()).isEqualTo("IDLE");
    }

    @Test
    void AC_049_indicatorsCompletedAfterAutoClose() throws Exception {
        long session = schedule(activePlan(eligibleHorse()), today());
        start(session, false);
        Instant started = Instant.parse(session(session).get("startedAt").asText());
        TestClock.shift(Duration.ofMinutes(121));
        sessionService.autoClose();
        TestClock.reset();

        Map<String, Object> body = indicators();
        body.put("actualEndTime", started.plus(Duration.ofMinutes(65)).toString());
        asJson(trainer, put("/api/sessions/" + session + "/indicators"), body).andExpect(status().isOk());
        JsonNode s = session(session);
        assertThat(s.get("state").asText()).isEqualTo("AUTO_CLOSED");
        assertThat(s.get("missingIndicators").asBoolean()).isFalse();
        assertThat(s.get("endTimeAutomatic").asBoolean()).isFalse();
        assertThat(Instant.parse(s.get("endedAt").asText())).isEqualTo(started.plus(Duration.ofMinutes(65)));
    }

    @Test
    void AC_050_thirtyMinuteGracePeriodApplied() throws Exception {
        asJson(manager, put("/api/settings/grace-period"), Map.of("minutes", 30)).andExpect(status().isOk());
        try {
            long session = schedule(activePlan(eligibleHorse()), today());
            start(session, false);
            TestClock.shift(Duration.ofMinutes(91));
            sessionService.autoClose();
            TestClock.reset();
            assertThat(state(session)).isEqualTo("AUTO_CLOSED");
        } finally {
            jdbc.update("update club_setting set value = '60' where key = 'grace_period_minutes'");
        }
    }

    @Test
    void AC_051_headTrainerCannotChangeGracePeriod() throws Exception {
        asJson(trainer, put("/api/settings/grace-period"), Map.of("minutes", 10)).andExpect(status().isForbidden());
        as(trainer, get("/api/settings/grace-period")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select value from club_setting where key = 'grace_period_minutes'", String.class))
                .isEqualTo("60");
    }
}
