package vn.racehorse.stock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A completion takes its medicine out of stock; planned doses feed the forecast (BR-172, BR-175, AC-339 to AC-344). */
class CareStockTest extends StockTestBase {

    private long careItem(long horse, String type, int due) throws Exception {
        return body(asJson(vet, post("/api/horses/" + horse + "/care"), Map.of("type", type, "name", "Mục " + type,
                "intervalDays", 90, "firstDue", today().plusDays(due).toString())).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private Map<String, Object> completion(Long supplyId, Object quantity) {
        Map<String, Object> m = new HashMap<>();
        m.put("dateDone", today().toString());
        m.put("performer", "BS. Test");
        m.put("supplyItemId", supplyId);
        m.put("quantity", quantity);
        return m;
    }

    @Test
    void AC_339_aVaccinationTakesTheMedicineOutOfStockAndKeepsThePriceOfThatMoment() throws Exception {
        JsonNode medicine = supply("MEDICINE", 40000, 5);
        long id = medicine.get("id").asLong();
        long horse = newHorse();
        long item = careItem(horse, "VACCINATION", 0);
        asJson(vet, post("/api/care/" + item + "/complete"), completion(id, 1)).andExpect(status().isOk());
        assertThat(balance(id)).isEqualTo(4);
        JsonNode line = movements(id).get(0);
        assertThat(line.get("kind").asText()).isEqualTo("USE_CARE");
        assertThat(line.get("delta").asLong()).isEqualTo(-1);
        assertThat(line.get("unitPrice").decimalValue()).isEqualByComparingTo("40000");
        assertThat(line.get("refType").asText()).isEqualTo("CARE_EVENT");

        // A later price change does not touch the line already written.
        asJson(manager, put("/api/supplies/" + id + "/price"), Map.of("price", 50000)).andExpect(status().isOk());
        assertThat(movements(id).get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("40000");

        JsonNode history = body(as(vet, get("/api/care/" + item + "/history")).andExpect(status().isOk()));
        JsonNode done = history.get(history.size() - 1);
        assertThat(done.get("supplyItemId").asLong()).isEqualTo(id);
        assertThat(done.get("quantity").asLong()).isEqualTo(1);
        assertThat(done.get("product").asText()).isEqualTo(medicine.get("name").asText());
    }

    @Test
    void AC_340_aQuantityAboveTheStockIsRefusedAndNothingChanges() throws Exception {
        long id = supply("MEDICINE", 40000, 2).get("id").asLong();
        long horse = newHorse();
        long item = careItem(horse, "DEWORMING", 0);
        asJson(vet, post("/api/care/" + item + "/complete"), completion(id, 3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(balance(id)).isEqualTo(2);
        JsonNode state = null;
        for (JsonNode n : body(as(vet, get("/api/horses/" + horse + "/care")))) {
            if (n.get("id").asLong() == item) state = n;
        }
        assertThat(state.get("nextDue").asText()).isEqualTo(today().toString());
        assertThat(state.get("lastCompletion").isNull()).isTrue();
        // A completion without the medicine, or with a fractional dose, is refused too.
        asJson(vet, post("/api/care/" + item + "/complete"), completion(null, 1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.supplyItemId").exists());
        asJson(vet, post("/api/care/" + item + "/complete"), completion(id, 1.5))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.quantity").exists());
    }

    @Test
    void AC_341_aMedicineAtZeroIsNotOfferedAndAFoodItemCannotBeChosen() throws Exception {
        long gone = supply("MEDICINE", 1000, 0).get("id").asLong();
        long there = supply("MEDICINE", 1000, 3).get("id").asLong();
        long food = supply("FEED", 1000, 3).get("id").asLong();
        List<Long> offered = ids(body(as(vet, get("/api/stock/medicines")).andExpect(status().isOk())));
        assertThat(offered).contains(there).doesNotContain(gone, food);
        List<Long> all = ids(body(as(vet, get("/api/stock/medicines").param("all", "true")).andExpect(status().isOk())));
        assertThat(all).contains(there, gone).doesNotContain(food);
        long horse = newHorse();
        long item = careItem(horse, "VACCINATION", 0);
        asJson(vet, post("/api/care/" + item + "/complete"), completion(food, 1)).andExpect(status().isBadRequest());
        asJson(vet, post("/api/care/" + item + "/complete"), completion(gone, 1)).andExpect(status().isConflict());
    }

    @Test
    void AC_342_aHoofCheckUsesNoStockAndRefusesAMedicine() throws Exception {
        long id = supply("MEDICINE", 1000, 3).get("id").asLong();
        long horse = newHorse();
        long item = careItem(horse, "HOOF_CHECK", 0);
        asJson(vet, post("/api/care/" + item + "/complete"), completion(id, 1)).andExpect(status().isBadRequest());
        asJson(vet, post("/api/care/" + item + "/complete"),
                Map.of("dateDone", today().toString(), "performer", "Thợ móng", "product", "Dầu móng"))
                .andExpect(status().isOk());
        assertThat(balance(id)).isEqualTo(3);
    }

    @Test
    void AC_343_aPlannedDoseThatStockCannotCoverBeforeItFallsDueIsCanNhap() throws Exception {
        long id = supply("MEDICINE", 1000, 30).get("id").asLong();
        assertThat(itemOf(id).get("state").asText()).isEqualTo("OK");
        long horse = newHorse();
        long item = careItem(horse, "VACCINATION", 5);
        // A dose the stock covers keeps the item OK; one it cannot cover turns it to Cần nhập before the due date.
        asJson(vet, put("/api/care-items/" + item + "/plan"), Map.of("supplyItemId", id, "quantity", 10))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plannedQuantity").value(10));
        assertThat(itemOf(id).get("state").asText()).isEqualTo("OK");
        asJson(vet, put("/api/care-items/" + item + "/plan"), Map.of("supplyItemId", id, "quantity", 40))
                .andExpect(status().isOk());
        assertThat(itemOf(id).get("state").asText()).isEqualTo("RESTOCK");
        // A delivery that is expected in time counts again: 30 + 100 - 40 is enough.
        asJson(manager, post("/api/supplies/" + id + "/expected-receipts"),
                Map.of("expectedDate", today().plusDays(1).toString(), "quantity", 100)).andExpect(status().isCreated());
        assertThat(itemOf(id).get("state").asText()).isEqualTo("OK");
        as(vet, delete("/api/care-items/" + item + "/plan")).andExpect(status().isOk());
        asJson(vet, put("/api/care-items/" + item + "/plan"), Map.of("supplyItemId", id, "quantity", 0))
                .andExpect(status().isBadRequest());
        asJson(vet, put("/api/care-items/" + item + "/plan"), Map.of("supplyItemId", supply("FEED", 1, 1).get("id").asLong(),
                "quantity", 2)).andExpect(status().isBadRequest());
        // A planned dose due beyond the warning window does not count yet.
        long later = careItem(horse, "DEWORMING", 30);
        asJson(vet, put("/api/care-items/" + later + "/plan"), Map.of("supplyItemId", id, "quantity", 500)).andExpect(status().isOk());
        assertThat(itemOf(id).get("state").asText()).isEqualTo("OK");
    }

    @Test
    void AC_344_theVeterinarianSeesQuantitiesButNeverAPriceAndOtherRolesAreRefused() throws Exception {
        long id = supply("MEDICINE", 40000, 5).get("id").asLong();
        JsonNode medicine = null;
        for (JsonNode m : body(as(vet, get("/api/stock/medicines")).andExpect(status().isOk()))) {
            if (m.get("id").asLong() == id) medicine = m;
        }
        assertThat(medicine.get("quantity").asLong()).isEqualTo(5);
        assertThat(medicine.has("unitPrice")).isFalse();
        assertThat(medicine.has("price")).isFalse();
        for (var who : List.of(trainer, owner, defaultGroom())) {
            as(who, get("/api/stock/medicines")).andExpect(status().isForbidden());
        }
        long horse = newHorse();
        long item = careItem(horse, "VACCINATION", 0);
        asJson(vet, post("/api/care/" + item + "/complete"), completion(id, 1)).andExpect(status().isOk());
        for (JsonNode e : body(as(vet, get("/api/care/" + item + "/history")))) {
            assertThat(e.has("unitPrice")).isFalse();
        }
        assertThat(LocalDate.now()).isNotNull();
    }

    private static List<Long> ids(JsonNode nodes) {
        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        nodes.forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }
}
