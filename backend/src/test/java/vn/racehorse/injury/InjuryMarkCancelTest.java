package vn.racehorse.injury;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.training.TrainingTestBase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cancelling a mark created by mistake, and dates that are never entered (BR-112, BR-113, DEC-037, DEC-039). */
class InjuryMarkCancelTest extends TrainingTestBase {

    private long newMark(long horse) throws Exception {
        long exam = body(asJson(vet, post("/api/horses/" + horse + "/examinations"),
                Map.of("diagnosis", "Viêm khớp", "bodyRegion", "LF_FETLOCK")).andExpect(status().isCreated()))
                .get("id").asLong();
        return body(asJson(vet, post("/api/horses/" + horse + "/injury-marks"),
                Map.of("regionCode", "LF_FETLOCK", "examinationId", exam)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private JsonNode marks(long horse) throws Exception {
        return body(as(vet, get("/api/horses/" + horse + "/injury-marks")).andExpect(status().isOk()));
    }

    private void moveTo(long mark, String status) throws Exception {
        asJson(vet, post("/api/injury-marks/" + mark + "/status"), Map.of("status", status)).andExpect(status().isOk());
    }

    @Test
    void AC_174_mistakenMarkCancelledKeptInHistoryAndAudited() throws Exception {
        long horse = newHorse();
        long mark = newMark(horse);
        JsonNode cancelled = body(asJson(vet, post("/api/injury-marks/" + mark + "/cancel"),
                Map.of("reason", "chọn nhầm chân")).andExpect(status().isOk()));
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        JsonNode last = cancelled.get("history").get(cancelled.get("history").size() - 1);
        assertThat(last.get("from").asText()).isEqualTo("NEW");
        assertThat(last.get("to").asText()).isEqualTo("CANCELLED");
        assertThat(last.get("note").asText()).isEqualTo("chọn nhầm chân");
        // Still listed (history) with its status, so the client can leave it out of the model and active list.
        JsonNode listed = marks(horse);
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.get(0).get("status").asText()).isEqualTo("CANCELLED");
        List<String> actions = StreamSupport.stream(
                        body(as(manager, get("/api/audit").param("actorId", vet.id().toString()))).spliterator(), false)
                .filter(e -> e.get("targetId").asLong() == horse).map(e -> e.get("action").asText()).toList();
        assertThat(actions).contains("INJURY_MARK_CANCELLED");
    }

    @Test
    void AC_175_reasonRequired() throws Exception {
        long horse = newHorse();
        long mark = newMark(horse);
        for (Object reason : new Object[]{null, "   "}) {
            Map<String, Object> m = new HashMap<>();
            m.put("reason", reason);
            asJson(vet, post("/api/injury-marks/" + mark + "/cancel"), m)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.reason").exists());
        }
        // The generic status change cannot cancel without the reason rule.
        asJson(vet, post("/api/injury-marks/" + mark + "/status"), Map.of("status", "CANCELLED"))
                .andExpect(status().isBadRequest());
        assertThat(marks(horse).get(0).get("status").asText()).isEqualTo("NEW");
    }

    @Test
    void AC_176_onlyANewMarkCanBeCancelled() throws Exception {
        long horse = newHorse();
        long recovering = newMark(horse);
        moveTo(recovering, "RECOVERING");
        long recovered = newMark(horse);
        moveTo(recovered, "RECOVERED");
        for (long mark : new long[]{recovering, recovered}) {
            asJson(vet, post("/api/injury-marks/" + mark + "/cancel"), Map.of("reason", "nhầm"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Mới")));
        }
    }

    @Test
    void AC_177_cancelledMarkIsFinalAndNeverRecovered() throws Exception {
        long horse = newHorse();
        long mark = newMark(horse);
        asJson(vet, post("/api/injury-marks/" + mark + "/cancel"), Map.of("reason", "nhầm ngựa")).andExpect(status().isOk());
        for (String next : List.of("NEW", "RECOVERING", "RECOVERED")) {
            asJson(vet, post("/api/injury-marks/" + mark + "/status"), Map.of("status", next))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_MARK_TRANSITION"));
        }
        asJson(vet, post("/api/injury-marks/" + mark + "/cancel"), Map.of("reason", "lần nữa"))
                .andExpect(status().isConflict());
        JsonNode m = marks(horse).get(0);
        assertThat(m.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(StreamSupport.stream(m.get("history").spliterator(), false).map(e -> e.get("to").asText()))
                .doesNotContain("RECOVERED");
    }

    @Test
    void AC_178_changeIsDatedTodayAndNoDateCanBeEntered() throws Exception {
        long horse = newHorse();
        long mark = newMark(horse);
        Map<String, Object> withDate = new HashMap<>();
        withDate.put("status", "RECOVERING");
        withDate.put("date", today().minusDays(10).toString());
        JsonNode updated = body(asJson(vet, post("/api/injury-marks/" + mark + "/status"), withDate)
                .andExpect(status().isOk()));
        JsonNode last = updated.get("history").get(updated.get("history").size() - 1);
        assertThat(last.get("date").asText()).isEqualTo(today().toString());

        long other = newMark(horse);
        JsonNode cancelled = body(asJson(vet, post("/api/injury-marks/" + other + "/cancel"),
                Map.of("reason", "nhầm", "date", today().minusDays(3).toString())).andExpect(status().isOk()));
        JsonNode cancelEvent = cancelled.get("history").get(cancelled.get("history").size() - 1);
        assertThat(cancelEvent.get("date").asText()).isEqualTo(today().toString());
    }

    @Test
    void AC_179_headTrainerCannotCancel() throws Exception {
        long horse = newHorse();
        long mark = newMark(horse);
        asJson(trainer, post("/api/injury-marks/" + mark + "/cancel"), Map.of("reason", "nhầm"))
                .andExpect(status().isForbidden());
        assertThat(marks(horse).get(0).get("status").asText()).isEqualTo("NEW");
    }
}
