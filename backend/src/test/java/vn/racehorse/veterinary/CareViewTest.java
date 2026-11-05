package vn.racehorse.veterinary;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.training.TrainingTestBase;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The compact care view: state, next due and the latest completion, never notes or batch (BR-187, AC-371). */
class CareViewTest extends TrainingTestBase {

    private long careItem(long horse, String type, String name, int interval, LocalDate firstDue) throws Exception {
        return body(asJson(vet, post("/api/horses/" + horse + "/care"),
                Map.of("type", type, "name", name, "intervalDays", interval, "firstDue", firstDue.toString()))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode item(long horse, long id, String... params) throws Exception {
        var request = get("/api/horses/" + horse + "/care");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        for (JsonNode n : body(as(vet, request).andExpect(status().isOk()))) {
            if (n.get("id").asLong() == id) return n;
        }
        return null;
    }

    /** A medicine in stock for the completion of a vaccination or deworming (BR-172). */
    private long medicine(String name) throws Exception {
        return body(asJson(manager, post("/api/supplies"), Map.of("name", name, "category", "MEDICINE", "unitId", unitId("liều"),
                "price", 1000, "openingQuantity", 10)).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void AC_371_theRowCarriesStateNextDueAndTheLatestCompletionButNoNoteOrBatch() throws Exception {
        long horse = newHorse();
        long id = careItem(horse, "VACCINATION", "Uốn ván", 365, today().minusDays(2));
        JsonNode never = item(horse, id);
        assertThat(never.get("state").asText()).isEqualTo("OVERDUE");
        assertThat(never.get("lastCompletion").isNull()).isTrue();
        assertThat(never.get("hasDetails").asBoolean()).isFalse();

        String vaccine = unique("Vắc-xin A");
        long vaccineId = medicine(vaccine);
        asJson(vet, post("/api/care/" + id + "/complete"), Map.of("dateDone", today().toString(), "performer", "BS. Lan",
                "supplyItemId", vaccineId, "quantity", 1, "batch", "LÔ-77", "note", "Ngựa ngoan")).andExpect(status().isOk());
        JsonNode done = item(horse, id);
        assertThat(done.get("lastCompletion").get("dateDone").asText()).isEqualTo(today().toString());
        assertThat(done.get("lastCompletion").get("product").asText()).isEqualTo(vaccine);
        assertThat(done.get("lastCompletion").get("performer").asText()).isEqualTo("BS. Lan");
        assertThat(done.get("hasDetails").asBoolean()).isTrue();
        assertThat(done.toString()).doesNotContain("LÔ-77").doesNotContain("Ngựa ngoan");
        assertThat(done.get("state").asText()).isEqualTo("OK");
    }

    @Test
    void AC_372_theDetailsOpenOnRequestFromTheHistory() throws Exception {
        long horse = newHorse();
        long id = careItem(horse, "DEWORMING", "Ivermectin", 90, today());
        asJson(vet, post("/api/care/" + id + "/complete"), Map.of("dateDone", today().toString(), "performer", "BS. Lan",
                "supplyItemId", medicine(unique("Thuốc B")), "quantity", 1, "batch", "LÔ-12", "note", "Ghi chú"))
                .andExpect(status().isOk());
        JsonNode history = body(as(vet, get("/api/care/" + id + "/history")).andExpect(status().isOk()));
        assertThat(history.toString()).contains("LÔ-12").contains("Ghi chú");
    }

    @Test
    void theCareListsFilterByTypeAndStateAndSearchByName() throws Exception {
        long horse = newHorse();
        long hoof = careItem(horse, "HOOF_CHECK", "Kiểm tra móng " + unique("x"), 60, today().minusDays(1));
        long vacc = careItem(horse, "VACCINATION", "Cúm ngựa " + unique("x"), 180, today().plusDays(60));
        assertThat(item(horse, hoof, "type", "HOOF_CHECK")).isNotNull();
        assertThat(item(horse, vacc, "type", "HOOF_CHECK")).isNull();
        assertThat(item(horse, hoof, "state", "OVERDUE")).isNotNull();
        assertThat(item(horse, vacc, "state", "OVERDUE")).isNull();
        assertThat(item(horse, vacc, "q", "cum ngua")).isNotNull();
        assertThat(item(horse, hoof, "q", "cum ngua")).isNull();
    }

    @Test
    void otherRolesAreStillRefused() throws Exception {
        long horse = newHorse();
        as(trainer, get("/api/horses/" + horse + "/care")).andExpect(status().isForbidden());
        as(owner, get("/api/care/due")).andExpect(status().isForbidden());
    }
}
