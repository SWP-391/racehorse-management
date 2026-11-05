package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The planned end (start plus planned duration) and the actual end of a session (BR-190, AC-380). */
class SessionEndTimeTest extends TrainingTestBase {

    private JsonNode sessionAt(long plan, String start, int minutes) throws Exception {
        Map<String, Object> body = sessionBody(today().plusDays(1), "GALLOP", false);
        body.put("startTime", start);
        body.put("plannedDurationMin", minutes);
        return body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), body).andExpect(status().isCreated()));
    }

    @Test
    void AC_380_sevenAndAnHourEndsAtEight() throws Exception {
        JsonNode s = sessionAt(activePlan(eligibleHorse()), "07:00", 60);
        assertThat(s.get("plannedEnd").asText()).startsWith("08:00");
        assertThat(s.get("plannedEndNextDay").asBoolean()).isFalse();
        assertThat(s.get("endedAt").isNull()).isTrue();
    }

    @Test
    void aSessionCrossingMidnightSaysSo() throws Exception {
        JsonNode s = sessionAt(activePlan(eligibleHorse()), "23:30", 60);
        assertThat(s.get("plannedEnd").asText()).startsWith("00:30");
        assertThat(s.get("plannedEndNextDay").asBoolean()).isTrue();
    }

    @Test
    void theActualEndShowsOnceTheSessionEnded_andTheOwnerScheduleCarriesBoth() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long id = schedule(plan, today());
        start(id, false);
        end(id);
        JsonNode ended = session(id);
        assertThat(ended.get("endedAt").isNull()).isFalse();
        assertThat(ended.get("plannedEnd").isNull()).isFalse();
        JsonNode schedule = body(as(owner, get("/api/horses/" + horse + "/schedule")).andExpect(status().isOk()));
        JsonNode row = null;
        for (JsonNode n : schedule) {
            if (n.get("id").asLong() == id) row = n;
        }
        assertThat(row).isNotNull();
        assertThat(row.get("endedAt").isNull()).isFalse();
        assertThat(row.get("plannedEnd").isNull()).isFalse();
    }
}
