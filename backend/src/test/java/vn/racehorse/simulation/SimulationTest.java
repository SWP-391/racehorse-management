package vn.racehorse.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import org.springframework.beans.factory.annotation.Autowired;
import vn.racehorse.common.TestClock;
import vn.racehorse.training.TrainingTestBase;
import vn.racehorse.simulation.service.SimulatedTelemetrySource;
import vn.racehorse.simulation.service.SimulationService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Simulated live monitoring, alerts and the end-of-session summary (REQ-005, BR-050 to BR-053). */
class SimulationTest extends TrainingTestBase {

    @Autowired
    SimulationService simulation;
    @Autowired
    SimulatedTelemetrySource source;

    private long runningSession(long horse) throws Exception {
        long session = schedule(activePlan(horse), today());
        start(session, false);
        return session;
    }

    private JsonNode live(long horse) throws Exception {
        return body(as(trainer, get("/api/horses/" + horse + "/live")).andExpect(status().isOk()));
    }

    private void threshold(long horse, String measure, Object limit) throws Exception {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("measure", measure);
        body.put("limit", limit);
        asJson(trainer, put("/api/horses/" + horse + "/thresholds"), body).andExpect(status().isOk());
    }

    private JsonNode alerts(long horse) throws Exception {
        return body(as(trainer, get("/api/horses/" + horse + "/alerts")).andExpect(status().isOk()));
    }

    @Test
    void theSimulationIsDeterministicAndPlausible() {
        Instant start = Instant.parse("2026-10-21T07:00:00Z");
        assertThat(source.sample(7, start, 40)).isEqualTo(source.sample(7, start, 40));
        assertThat(source.sample(7, start, 40)).isNotEqualTo(source.sample(8, start, 40));
        double early = 0, late = 0;
        for (int t = 0; t < 10; t++) {
            early += source.sample(7, start, t).heartRate();
            late += source.sample(7, start, 400 + t).heartRate();
        }
        assertThat(late / 10).isGreaterThan(early / 10 + 80);
        for (int t = 0; t < 1800; t += 7) {
            var s = source.sample(7, start, t);
            assertThat(s.heartRate()).isBetween(30, 215);
            assertThat(s.speed()).isBetween(0.0, 60.0);
        }
        assertThat(source.dataSource().name()).isEqualTo("SIMULATED");
    }

    @Test
    void AC_055_liveValuesChangeDuringTheSessionAndAreLabelledSimulated() throws Exception {
        long horse = eligibleHorse();
        runningSession(horse);
        JsonNode first = live(horse);
        assertThat(first.get("active").asBoolean()).isTrue();
        assertThat(first.get("label").asText()).isEqualTo("DỮ LIỆU MÔ PHỎNG");
        assertThat(first.get("dataSource").asText()).isEqualTo("SIMULATED");
        int hrAtStart = first.get("current").get("heartRate").asInt();

        TestClock.shift(Duration.ofSeconds(90));
        JsonNode later = live(horse);
        assertThat(later.get("elapsedSeconds").asInt()).isBetween(89, 92);
        assertThat(later.get("current").get("heartRate").asInt()).isGreaterThan(hrAtStart + 30);
        assertThat(later.get("recent").size()).isEqualTo(60);
        assertThat(later.get("current").get("speed").asDouble()).isGreaterThan(20);
    }

    @Test
    void AC_059_noSimulatedDataWhileTheHorseIsIdle() throws Exception {
        long horse = eligibleHorse();
        JsonNode idle = live(horse);
        assertThat(idle.get("active").asBoolean()).isFalse();
        assertThat(idle.get("current").isNull()).isTrue();
        assertThat(idle.get("recent").size()).isZero();
        long session = runningSession(horse);
        assertThat(live(horse).get("active").asBoolean()).isTrue();
        end(session);
        assertThat(live(horse).get("active").asBoolean()).isFalse();
    }

    @Test
    void AC_060_ownerCannotOpenTheLiveViewOrSetAThreshold() throws Exception {
        long horse = eligibleHorse();
        runningSession(horse);
        as(owner, get("/api/horses/" + horse + "/live")).andExpect(status().isForbidden());
        asJson(owner, put("/api/horses/" + horse + "/thresholds"), Map.of("measure", "HEART_RATE", "limit", 150))
                .andExpect(status().isForbidden());
        as(vet, get("/api/horses/" + horse + "/live")).andExpect(status().isForbidden());
        asJson(vet, put("/api/horses/" + horse + "/thresholds"), Map.of("measure", "HEART_RATE", "limit", 150))
                .andExpect(status().isForbidden());
    }

    @Test
    void AC_399_onlyTheHeadTrainerSetsAThreshold() throws Exception {
        long horse = eligibleHorse();
        for (Role role : new Role[] {Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER, Role.CLUB_MANAGER}) {
            asJson(account(role), put("/api/horses/" + horse + "/thresholds"), Map.of("measure", "HEART_RATE", "limit", 150))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void AC_056_exceededThresholdNotifiesHeadTrainerAndVeterinarianWithoutChangingHealth() throws Exception {
        long horse = eligibleHorse();
        threshold(horse, "HEART_RATE", 100);
        runningSession(horse);
        assertThat(alerts(horse).size()).isZero();

        TestClock.shift(Duration.ofSeconds(120));
        simulation.evaluate();
        TestClock.reset();

        JsonNode list = alerts(horse);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("measure").asText()).isEqualTo("HEART_RATE");
        assertThat(list.get(0).get("value").asInt()).isGreaterThan(100);
        assertThat(list.get(0).get("dataSource").asText()).isEqualTo("SIMULATED");
        assertThat(list.get(0).get("label").asText()).isEqualTo("DỮ LIỆU MÔ PHỎNG");
        assertThat(hasNotification(trainer, "Cảnh báo (DỮ LIỆU MÔ PHỎNG)")).isTrue();
        assertThat(hasNotification(vet, "Cảnh báo (DỮ LIỆU MÔ PHỎNG)")).isTrue();
        JsonNode h = health(horse);
        assertThat(h.get("status").asText()).isEqualTo("ELIGIBLE");
        assertThat(h.get("lock").isNull()).isTrue();
    }

    @Test
    void AC_057_noThresholdNoAlert() throws Exception {
        long horse = eligibleHorse();
        runningSession(horse);
        TestClock.shift(Duration.ofMinutes(10));
        simulation.evaluate();
        live(horse);
        TestClock.reset();
        assertThat(alerts(horse).size()).isZero();
        assertThat(as(trainer, get("/api/horses/" + horse + "/thresholds")).andReturn().getResponse().getContentAsString())
                .isEqualTo("[]");
    }

    @Test
    void anAlertIsRaisedOncePerSessionAndMeasure() throws Exception {
        long horse = eligibleHorse();
        threshold(horse, "HEART_RATE", 100);
        threshold(horse, "SPEED", 30);
        runningSession(horse);
        TestClock.shift(Duration.ofSeconds(300));
        simulation.evaluate();
        simulation.evaluate();
        live(horse);
        TestClock.reset();
        JsonNode list = alerts(horse);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.findValues("measure").stream().map(JsonNode::asText)).containsExactlyInAnyOrder("HEART_RATE", "SPEED");
        // The alert list is searched by the measure's code or name, without tone marks (BR-185).
        JsonNode heart = pageBody(as(trainer, get("/api/horses/" + horse + "/alerts").param("q", "nhip tim")).andExpect(status().isOk()));
        assertThat(heart.get("total").asInt()).isEqualTo(1);
        assertThat(heart.get("items").get(0).get("measure").asText()).isEqualTo("HEART_RATE");
        assertThat(pageBody(as(trainer, get("/api/horses/" + horse + "/alerts").param("q", "speed"))).get("total").asInt()).isEqualTo(1);
        assertThat(pageBody(as(trainer, get("/api/horses/" + horse + "/alerts").param("q", "khong co"))).get("total").asInt()).isZero();
    }

    @Test
    void aThresholdSetMidSessionIgnoresEarlierSeconds() throws Exception {
        long horse = eligibleHorse();
        runningSession(horse);
        TestClock.shift(Duration.ofSeconds(200));
        threshold(horse, "HEART_RATE", 100);
        simulation.evaluate();
        TestClock.reset();
        JsonNode list = alerts(horse);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("offsetSecond").asInt()).isGreaterThanOrEqualTo(199);
    }

    @Test
    void thresholdsAreValidatedClearedAndNeverDefaulted() throws Exception {
        long horse = eligibleHorse();
        asJson(trainer, put("/api/horses/" + horse + "/thresholds"), Map.of("measure", "SPEED", "limit", 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
        threshold(horse, "SPEED", 45.5);
        assertThat(body(as(trainer, get("/api/horses/" + horse + "/thresholds"))).get(0).get("limit").asDouble()).isEqualTo(45.5);
        threshold(horse, "SPEED", null);
        assertThat(body(as(trainer, get("/api/horses/" + horse + "/thresholds"))).size()).isZero();
    }

    @Test
    void AC_058_averagesFilledAtTheEndAndMarkedSimulated() throws Exception {
        long horse = eligibleHorse();
        long session = runningSession(horse);
        TestClock.shift(Duration.ofSeconds(120));
        end(session);
        TestClock.reset();

        JsonNode s = session(session);
        assertThat(s.get("avgHeartRateSource").asText()).isEqualTo("SIMULATED");
        assertThat(s.get("maxSpeedSource").asText()).isEqualTo("SIMULATED");
        Instant started = Instant.parse(s.get("startedAt").asText());
        int end = (int) Duration.between(started, Instant.parse(s.get("endedAt").asText())).getSeconds();
        long sum = 0;
        double max = 0;
        for (int t = 0; t <= end; t++) {
            var sample = source.sample(session, started, t);
            sum += sample.heartRate();
            max = Math.max(max, sample.speed());
        }
        assertThat(s.get("avgHeartRate").asInt()).isEqualTo((int) Math.round((double) sum / (end + 1)));
        assertThat(s.get("maxSpeed").asDouble()).isEqualTo(Math.round(max * 10) / 10.0);
    }

    @Test
    void aManuallyEnteredValueStaysManualAndOnlyTheEmptyOneIsSimulated() throws Exception {
        long horse = eligibleHorse();
        long session = runningSession(horse);
        Map<String, Object> body = indicators();
        body.put("avgHeartRate", 150);
        // let the session run, so the simulated speed is above zero (an instant session has no speed to fill)
        TestClock.shift(Duration.ofSeconds(90));
        try {
            asJson(trainer, post("/api/sessions/" + session + "/end"), body).andExpect(status().isOk());
        } finally {
            TestClock.reset();
        }
        JsonNode s = session(session);
        assertThat(s.get("avgHeartRate").asInt()).isEqualTo(150);
        assertThat(s.get("avgHeartRateSource").asText()).isEqualTo("MANUAL");
        assertThat(s.get("maxSpeedSource").asText()).isEqualTo("SIMULATED");
    }

    @Test
    void autoCloseAlsoFillsSimulatedValues() throws Exception {
        long horse = eligibleHorse();
        long session = runningSession(horse);
        TestClock.shift(Duration.ofMinutes(125));
        sessionService.autoClose();
        TestClock.reset();
        JsonNode s = session(session);
        assertThat(s.get("state").asText()).isEqualTo("AUTO_CLOSED");
        assertThat(s.get("avgHeartRateSource").asText()).isEqualTo("SIMULATED");
        assertThat(s.get("avgHeartRate").asInt()).isBetween(60, 190);
    }

    @Test
    void AC_066_boardTrendCarriesEachSessionsDataSourceAndNoFitnessScore() throws Exception {
        long horse = eligibleHorse();
        long session = runningSession(horse);
        end(session);
        JsonNode rows = body(as(trainer, get("/api/progress")).andExpect(status().isOk()));
        JsonNode row = null;
        for (JsonNode r : rows) {
            if (r.get("horseId").asLong() == horse) row = r;
        }
        assertThat(row).isNotNull();
        JsonNode point = row.get("trend").get(0);
        assertThat(point.get("avgHeartRateSource").asText()).isEqualTo("SIMULATED");
        assertThat(point.get("distanceM").asInt()).isEqualTo(1500);
        assertThat(point.get("intensity").asText()).isEqualTo("MEDIUM");
        assertThat(rows.toString().toLowerCase()).doesNotContain("fitnessscore").doesNotContain("fitness_score");
    }

    @Test
    void aSessionEndedAtOnceHasNoSpeedToRecordAndStillEnds() throws Exception {
        long horse = eligibleHorse();
        long session = runningSession(horse);
        end(session);
        JsonNode s = session(session);
        assertThat(s.get("state").asText()).isEqualTo("COMPLETED");
        assertThat(s.get("avgHeartRateSource").asText()).isEqualTo("SIMULATED");
        if (s.get("maxSpeed").isNull()) {
            assertThat(s.get("maxSpeedSource").isNull()).isTrue();
        } else {
            assertThat(s.get("maxSpeed").asDouble()).isGreaterThan(0);
        }
    }
}
