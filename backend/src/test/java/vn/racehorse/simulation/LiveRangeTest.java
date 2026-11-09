package vn.racehorse.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.TestClock;
import vn.racehorse.training.TrainingTestBase;
import vn.racehorse.simulation.service.SimulatedTelemetrySource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The live panel's range: threshold lines, session min, max and average, and the time window (BR-189, AC-377 to AC-381). */
class LiveRangeTest extends TrainingTestBase {

    @Autowired
    SimulatedTelemetrySource source;

    private long running(long horse) throws Exception {
        long session = schedule(activePlan(horse), today());
        start(session, false);
        return session;
    }

    private JsonNode live(long horse, String window) throws Exception {
        var request = get("/api/horses/" + horse + "/live");
        if (window != null) request.param("window", window);
        return body(as(trainer, request).andExpect(status().isOk()));
    }

    private double[] recompute(long sessionId, Instant startedAt, int from, int to) {
        int hrMin = Integer.MAX_VALUE, hrMax = Integer.MIN_VALUE;
        double spMax = -1, sumHr = 0;
        for (int t = from; t <= to; t++) {
            var s = source.sample(sessionId, startedAt, t);
            hrMin = Math.min(hrMin, s.heartRate());
            hrMax = Math.max(hrMax, s.heartRate());
            spMax = Math.max(spMax, s.speed());
            sumHr += s.heartRate();
        }
        return new double[] {hrMin, hrMax, spMax, sumHr / (to - from + 1)};
    }

    @Test
    void AC_377_theAnswerCarriesTheThresholdLinesAndTheFigures() throws Exception {
        long horse = eligibleHorse();
        running(horse);
        HashMap<String, Object> body = new HashMap<>();
        body.put("measure", "HEART_RATE");
        body.put("limit", 195);
        asJson(trainer, put("/api/horses/" + horse + "/thresholds"), body).andExpect(status().isOk());
        TestClock.shift(Duration.ofSeconds(100));
        JsonNode live = live(horse, null);
        assertThat(live.get("thresholds")).hasSize(1);
        assertThat(live.get("thresholds").get(0).get("measure").asText()).isEqualTo("HEART_RATE");
        assertThat(live.get("thresholds").get(0).get("limit").asInt()).isEqualTo(195);
        JsonNode summary = live.get("summary");
        assertThat(summary.get("windowSeconds").asInt()).isEqualTo(60);
        JsonNode hr = summary.get("heartRate");
        assertThat(hr.get("min").asDouble()).isLessThanOrEqualTo(hr.get("avg").asDouble());
        assertThat(hr.get("avg").asDouble()).isLessThanOrEqualTo(hr.get("max").asDouble());
        assertThat(live.get("label").asText()).isEqualTo("DỮ LIỆU MÔ PHỎNG");
    }

    @Test
    void AC_378_everyWindowGivesFiguresThatMatchADirectRecomputation() throws Exception {
        long horse = eligibleHorse();
        long session = running(horse);
        Instant startedAt = Instant.parse(session(session).get("startedAt").asText());
        TestClock.shift(Duration.ofSeconds(420));
        for (String window : new String[] {"ONE_MINUTE", "FIVE_MINUTES", "SESSION"}) {
            JsonNode live = live(horse, window);
            int now = live.get("elapsedSeconds").asInt();
            int from = switch (window) {
                case "ONE_MINUTE" -> Math.max(0, now - 59);
                case "FIVE_MINUTES" -> Math.max(0, now - 299);
                default -> 0;
            };
            double[] expected = recompute(session, startedAt, from, now);
            JsonNode hr = live.get("summary").get("heartRate");
            assertThat(hr.get("min").asDouble()).as(window + " min").isEqualTo(expected[0]);
            assertThat(hr.get("max").asDouble()).as(window + " max").isEqualTo(expected[1]);
            assertThat(hr.get("avg").asDouble()).as(window + " avg").isCloseTo(expected[3], org.assertj.core.data.Offset.offset(0.06));
            assertThat(live.get("summary").get("speed").get("max").asDouble()).as(window + " speed max").isEqualTo(expected[2]);
            assertThat(live.get("recent").size()).isLessThanOrEqualTo(300);
        }
        assertThat(live(horse, "ONE_MINUTE").get("recent")).hasSize(60);
    }

    @Test
    void aLongSessionStaysFastAndKeepsAtMostThreeHundredPoints() throws Exception {
        long horse = eligibleHorse();
        running(horse);
        TestClock.shift(Duration.ofHours(8));
        long begun = System.nanoTime();
        JsonNode live = live(horse, "SESSION");
        JsonNode again = live(horse, "SESSION");
        assertThat((System.nanoTime() - begun) / 1_000_000).isLessThan(5_000);
        assertThat(live.get("recent").size()).isBetween(2, 300);
        assertThat(live.get("summary").get("windowSeconds").asInt()).isGreaterThan(28_000);
        assertThat(again.get("summary").get("heartRate").get("min").asDouble())
                .isEqualTo(live.get("summary").get("heartRate").get("min").asDouble());
    }

    @Test
    void AC_379_withoutAThresholdThereIsNoLineButTheFiguresStay() throws Exception {
        long horse = eligibleHorse();
        running(horse);
        JsonNode live = live(horse, null);
        assertThat(live.get("thresholds")).isEmpty();
        assertThat(live.get("summary").get("heartRate").has("avg")).isTrue();
    }

    @Test
    void anIdleHorseStillAnswersNothing() throws Exception {
        long horse = eligibleHorse();
        JsonNode live = live(horse, "SESSION");
        assertThat(live.get("active").asBoolean()).isFalse();
        assertThat(live.get("recent")).isEmpty();
    }

    @Test
    void AC_381_onlyTheHeadTrainerReadsTheLiveRange() throws Exception {
        long horse = eligibleHorse();
        running(horse);
        for (Role role : new Role[] {Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER, Role.CLUB_MANAGER}) {
            as(account(role), get("/api/horses/" + horse + "/live").param("window", "SESSION")).andExpect(status().isForbidden());
        }
    }
}
