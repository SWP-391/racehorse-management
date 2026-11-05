package vn.racehorse.veterinary;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.training.TrainingTestBase;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Periodic care items, reminders and history (BR-061 to BR-065). */
class CareItemsTest extends TrainingTestBase {

    private long careItem(long horse, String type, String name, int interval, LocalDate firstDue) throws Exception {
        return body(asJson(vet, post("/api/horses/" + horse + "/care"),
                Map.of("type", type, "name", name, "intervalDays", interval, "firstDue", firstDue.toString()))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode due(long item) throws Exception {
        for (JsonNode n : body(as(vet, get("/api/care/due")).andExpect(status().isOk()))) {
            if (n.get("id").asLong() == item) return n;
        }
        return null;
    }

    private JsonNode itemOf(long horse, long item) throws Exception {
        for (JsonNode n : body(as(vet, get("/api/horses/" + horse + "/care")).andExpect(status().isOk()))) {
            if (n.get("id").asLong() == item) return n;
        }
        throw new AssertionError("item not found");
    }

    @Test
    void AC_095_dueSoonSevenDaysBefore() throws Exception {
        long horse = newHorse();
        long soon = careItem(horse, "DEWORMING", "Ivermectin", 90, today().plusDays(7));
        long later = careItem(horse, "VACCINATION", "Cúm ngựa", 180, today().plusDays(8));
        JsonNode item = due(soon);
        assertThat(item).isNotNull();
        assertThat(item.get("state").asText()).isEqualTo("DUE_SOON");
        assertThat(due(later)).isNull();
        assertThat(itemOf(horse, later).get("state").asText()).isEqualTo("OK");
    }

    @Test
    void AC_096_nextDueIsDateDonePlusIntervalAndPerformerStored() throws Exception {
        long horse = newHorse();
        LocalDate due = today().minusDays(3);
        long item = careItem(horse, "HOOF_CHECK", "Cắt móng", 60, due);
        LocalDate done = due.plusDays(2);
        asJson(vet, post("/api/care/" + item + "/complete"), Map.of("dateDone", done.toString(),
                "performer", "Thợ móng Hùng (ngoài CLB)", "product", "Dầu móng", "batch", "LOT-77", "note", "Móng tốt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDue").value(done.plusDays(60).toString()));
        JsonNode events = body(as(vet, get("/api/care/" + item + "/history")));
        assertThat(events.size()).isEqualTo(1);
        assertThat(events.get(0).get("kind").asText()).isEqualTo("COMPLETION");
        assertThat(events.get(0).get("performer").asText()).isEqualTo("Thợ móng Hùng (ngoài CLB)");
        assertThat(events.get(0).get("batch").asText()).isEqualTo("LOT-77");
        assertThat(events.get(0).get("previousDue").asText()).isEqualTo(due.toString());
    }

    @Test
    void AC_097_dueYesterdayShowsAsOverdue() throws Exception {
        long horse = newHorse();
        long item = careItem(horse, "VACCINATION", "Uốn ván", 365, today().minusDays(1));
        JsonNode n = due(item);
        assertThat(n).isNotNull();
        assertThat(n.get("state").asText()).isEqualTo("OVERDUE");
    }

    @Test
    void AC_098_postponedForQuarantinedHorseHistoryKept() throws Exception {
        long horse = newHorse();
        setStatus(horse, "QUARANTINED");
        long item = careItem(horse, "VACCINATION", "Cúm ngựa", 180, today().plusDays(2));
        LocalDate later = today().plusDays(30);
        asJson(vet, post("/api/care/" + item + "/postpone"), Map.of("newDue", later.toString(), "reason", "Ngựa đang cách ly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDue").value(later.toString()));
        JsonNode events = body(as(vet, get("/api/care/" + item + "/history")));
        assertThat(events.get(0).get("kind").asText()).isEqualTo("POSTPONEMENT");
        assertThat(events.get(0).get("note").asText()).isEqualTo("Ngựa đang cách ly");
        assertThat(events.get(0).get("previousDue").asText()).isEqualTo(today().plusDays(2).toString());
    }

    @Test
    void AC_099_overrideWithoutReasonRefused() throws Exception {
        long horse = newHorse();
        long item = careItem(horse, "DEWORMING", "Ivermectin", 90, today().plusDays(5));
        asJson(vet, post("/api/care/" + item + "/override"), Map.of("newDue", today().plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        assertThat(itemOf(horse, item).get("nextDue").asText()).isEqualTo(today().plusDays(5).toString());
        asJson(vet, post("/api/care/" + item + "/override"), Map.of("newDue", today().plusDays(1).toString(),
                "reason", "Mũi nhắc lại sớm")).andExpect(status().isOk());
        assertThat(body(as(vet, get("/api/care/" + item + "/history"))).get(0).get("kind").asText()).isEqualTo("OVERRIDE");
    }

    /** A medicine in stock for the completion of a vaccination or deworming (BR-172). */
    private long medicine(String name) throws Exception {
        return body(asJson(manager, post("/api/supplies"), Map.of("name", name, "category", "MEDICINE", "unitId", unitId("liều"),
                "price", 1000, "openingQuantity", 10)).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void AC_100_vaccinationDoesNotRestrictTraining() throws Exception {
        long horse = eligibleHorse();
        long item = careItem(horse, "VACCINATION", "Cúm ngựa", 180, today());
        long vaccine = medicine(unique("Vắc-xin cúm"));
        asJson(vet, post("/api/care/" + item + "/complete"), Map.of("dateDone", today().toString(),
                "performer", vet.displayName(), "supplyItemId", vaccine, "quantity", 1)).andExpect(status().isOk());
        long session = schedule(activePlan(horse), today());
        start(session, false);
        assertThat(state(session)).isEqualTo("IN_PROGRESS");
        assertThat(health(horse).get("lock").isNull()).isTrue();
    }

    @Test
    void AC_101_headTrainerCannotOpenTheListOrEditCare() throws Exception {
        long horse = newHorse();
        long item = careItem(horse, "DEWORMING", "Ivermectin", 90, today().plusDays(3));
        as(trainer, get("/api/care/due")).andExpect(status().isForbidden());
        as(trainer, get("/api/horses/" + horse + "/care")).andExpect(status().isForbidden());
        asJson(trainer, post("/api/horses/" + horse + "/care"), Map.of("type", "DEWORMING", "name", "x",
                "intervalDays", 30, "firstDue", today().plusDays(1).toString())).andExpect(status().isForbidden());
        asJson(trainer, post("/api/care/" + item + "/override"), Map.of("newDue", today().toString(), "reason", "x"))
                .andExpect(status().isForbidden());
        as(manager, get("/api/care/due")).andExpect(status().isForbidden());
    }

    @Test
    void careItemsCannotBeCreatedForAnArchivedHorse() throws Exception {
        long horse = newHorse();
        asJson(manager, post("/api/horses/" + horse + "/archive"), Map.of("reason", "SOLD")).andExpect(status().isOk());
        asJson(vet, post("/api/horses/" + horse + "/care"), Map.of("type", "DEWORMING", "name", "x", "intervalDays", 30,
                "firstDue", today().plusDays(1).toString())).andExpect(status().isConflict());
    }

    @Test
    void noIntervalIsBuiltInTheVeterinarianMustSetIt() throws Exception {
        long horse = newHorse();
        asJson(vet, post("/api/horses/" + horse + "/care"), Map.of("type", "VACCINATION", "name", "Cúm ngựa",
                "firstDue", today().plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.intervalDays").exists());
        List<JsonNode> all = StreamSupport.stream(body(as(vet, get("/api/horses/" + horse + "/care")).andExpect(status().isOk())).spliterator(), false).toList();
        assertThat(all).isEmpty();
    }
}
