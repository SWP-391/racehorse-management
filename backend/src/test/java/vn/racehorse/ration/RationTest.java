package vn.racehorse.ration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The approved ration: composing, versions, viewing and the link to the chore list (BR-137 to BR-145). */
class RationTest extends TrainingTestBase {

    private AccountPrincipal groom;
    private long oats;
    private long hay;
    private long vitaminE;

    @BeforeEach
    void fixtures() throws Exception {
        groom = account(Role.GROOM);
        oats = supply(unique("Yến mạch"), "FEED");
        hay = supply(unique("Cỏ khô"), "FEED");
        vitaminE = supply(unique("Vitamin E"), "FEED");
    }

    private long supply(String name, String category) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("category", category);
        m.put("unitId", unitId("kg"));
        m.put("price", 1000);
        m.put("openingQuantity", 100);
        return body(asJson(manager, post("/api/supplies"), m).andExpect(status().isCreated())).get("id").asLong();
    }

    private static Map<String, Object> item(String type, Long supplyId, Object quantity) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("supplyItemId", supplyId);
        m.put("quantity", quantity);
        return m;
    }

    @SafeVarargs
    private static Map<String, Object> meal(String name, String time, Map<String, Object>... items) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("time", time);
        m.put("items", List.of(items));
        return m;
    }

    @SafeVarargs
    private static Map<String, Object> ration(Map<String, Object>... meals) {
        return Map.of("meals", List.of(meals));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> basic() {
        return ration(
                meal("Bữa sáng", "06:00", item("GRAIN", oats, 2.5), item("HAY", hay, 4)),
                meal("Bữa tối", "18:00", item("VITAMIN", vitaminE, 0.1)));
    }

    private com.fasterxml.jackson.databind.JsonNode save(AccountPrincipal who, long horse, Object body, int expected) throws Exception {
        var result = asJson(who, put("/api/horses/" + horse + "/ration"), body);
        result.andExpect(status().is(expected));
        return body(result);
    }

    private JsonNode view(AccountPrincipal who, long horse) throws Exception {
        return body(as(who, get("/api/horses/" + horse + "/ration")).andExpect(status().isOk()));
    }

    private List<String> auditActions(AccountPrincipal actor) throws Exception {
        List<String> actions = new ArrayList<>();
        for (JsonNode e : body(as(manager, get("/api/audit").param("actorId", actor.id().toString())))) {
            actions.add(e.get("action").asText());
        }
        return actions;
    }

    @Test
    void AC_245_aRationIsComposedAndApprovedAtOnceAndAudited() throws Exception {
        long horse = newHorse();
        JsonNode saved = save(vet, horse, basic(), 200);
        JsonNode current = saved.get("current");
        assertThat(current.get("version").asInt()).isEqualTo(1);
        assertThat(current.get("meals")).hasSize(2);
        JsonNode first = current.get("meals").get(0);
        assertThat(first.get("name").asText()).isEqualTo("Bữa sáng");
        assertThat(first.get("time").asText()).isEqualTo("06:00");
        assertThat(first.get("items").get(0).get("type").asText()).isEqualTo("GRAIN");
        assertThat(first.get("items").get(0).get("quantity").decimalValue()).isEqualByComparingTo("2.5");
        assertThat(first.get("items").get(0).get("unit").asText()).isEqualTo("kg");
        assertThat(view(vet, horse).get("current").get("version").asInt()).isEqualTo(1);
        assertThat(auditActions(vet)).contains("RATION_SAVED");
    }

    @Test
    void AC_246_aChangeCreatesVersionTwoAndKeepsVersionOneInTheHistory() throws Exception {
        long horse = newHorse();
        save(vet, horse, basic(), 200);
        Map<String, Object> changed = ration(
                meal("Bữa sáng", "06:00", item("GRAIN", oats, 3), item("HAY", hay, 4)),
                meal("Bữa tối", "18:00", item("VITAMIN", vitaminE, 0.1)));
        JsonNode saved = save(vet, horse, changed, 200);
        assertThat(saved.get("current").get("version").asInt()).isEqualTo(2);
        JsonNode history = view(vet, horse).get("history");
        assertThat(history).hasSize(2);
        assertThat(history.get(1).get("version").asInt()).isEqualTo(1);
        assertThat(history.get(1).get("savedByName").asText()).isEqualTo(vet.displayName());
        assertThat(history.get(1).get("meals").get(0).get("items").get(0).get("quantity").decimalValue())
                .isEqualByComparingTo("2.5");
    }

    @Test
    void AC_247_structureErrorsAreRefusedNamingTheFieldAndTheRationIsUnchanged() throws Exception {
        long horse = newHorse();
        save(vet, horse, basic(), 200);
        @SuppressWarnings("unchecked")
        Map<String, Object> noItem = ration(meal("Bữa sáng", "06:00"));
        @SuppressWarnings("unchecked")
        Map<String, Object> blankName = ration(meal("  ", "06:00", item("GRAIN", oats, 1)));
        @SuppressWarnings("unchecked")
        Map<String, Object> repeated = ration(meal("Bữa sáng", "06:00", item("GRAIN", oats, 1)),
                meal("bữa SÁNG", "12:00", item("HAY", hay, 1)));
        @SuppressWarnings("unchecked")
        Map<String, Object> badTime = ration(meal("Bữa sáng", "25:61", item("GRAIN", oats, 1)));
        save(vet, horse, Map.of("meals", List.of()), 400);
        assertThat(save(vet, horse, noItem, 400).get("fields").toString()).contains("meals[0].items");
        assertThat(save(vet, horse, blankName, 400).get("fields").toString()).contains("meals[0].name");
        assertThat(save(vet, horse, repeated, 400).get("fields").toString()).contains("meals[1].name");
        assertThat(save(vet, horse, badTime, 400).get("fields").toString()).contains("meals[0].time");
        assertThat(view(vet, horse).get("current").get("version").asInt()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void AC_248_itemErrorsAreRefusedNamingTheField() throws Exception {
        long horse = newHorse();
        long medicine = supply(unique("Thuốc"), "MEDICINE");
        long off = supply(unique("Ngừng dùng"), "FEED");
        Map<String, Object> deactivate = new HashMap<>();
        deactivate.put("name", "Ngừng dùng " + off);
        deactivate.put("category", "FEED");
        deactivate.put("unitId", unitId("kg"));
        deactivate.put("active", false);
        asJson(manager, put("/api/supplies/" + off), deactivate).andExpect(status().isOk());
        for (Object quantity : new Object[] {0, -1, "abc"}) {
            assertThat(save(vet, horse, ration(meal("Sáng", "06:00", item("GRAIN", oats, quantity))), 400)
                    .get("fields").toString()).contains("quantity");
        }
        assertThat(save(vet, horse, ration(meal("Sáng", "06:00", item(null, oats, 1))), 400)
                .get("fields").toString()).contains("type");
        assertThat(save(vet, horse, ration(meal("Sáng", "06:00", item("GRAIN", 999999999L, 1))), 400)
                .get("fields").toString()).contains("supplyItemId");
        assertThat(save(vet, horse, ration(meal("Sáng", "06:00", item("GRAIN", medicine, 1))), 400)
                .get("fields").toString()).contains("supplyItemId");
        assertThat(save(vet, horse, ration(meal("Sáng", "06:00", item("GRAIN", off, 1))), 400)
                .get("fields").toString()).contains("supplyItemId");
        as(vet, get("/api/horses/" + horse + "/ration")).andExpect(jsonPath("$.current").doesNotExist());
    }

    @Test
    @SuppressWarnings("unchecked")
    void AC_249_theSameSupplyItemTwiceInAMealIsRefusedNamingIt() throws Exception {
        long horse = newHorse();
        JsonNode refused = save(vet, horse, ration(meal("Sáng", "06:00", item("GRAIN", oats, 1), item("GRAIN", oats, 2))), 400);
        assertThat(refused.get("fields").toString()).contains("supplyItemId").contains("Yến mạch");
    }

    @Test
    void AC_250_savingWithoutAChangeCreatesNoVersion() throws Exception {
        long horse = newHorse();
        save(vet, horse, basic(), 200);
        JsonNode again = save(vet, horse, basic(), 200);
        assertThat(again.get("current").get("version").asInt()).isEqualTo(1);
        assertThat(view(vet, horse).get("history")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void AC_251_theEffectiveDateIsTheDayRecordedAndCannotBeChosen() throws Exception {
        long horse = newHorse();
        Map<String, Object> body = new HashMap<>(basic());
        body.put("effectiveDate", LocalDate.now(CLUB).minusDays(30).toString());
        JsonNode saved = save(vet, horse, body, 200);
        assertThat(saved.get("current").get("effectiveDate").asText()).isEqualTo(LocalDate.now(CLUB).toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void AC_252_aDeactivatedItemStaysMarkedInactiveAndBlocksANewVersionUntilReplaced() throws Exception {
        long horse = newHorse();
        save(vet, horse, ration(meal("Sáng", "06:00", item("GRAIN", oats, 1), item("HAY", hay, 2))), 200);
        Map<String, Object> off = new HashMap<>();
        off.put("name", "Yến mạch cũ " + oats);
        off.put("category", "FEED");
        off.put("unitId", unitId("kg"));
        off.put("active", false);
        asJson(manager, put("/api/supplies/" + oats), off).andExpect(status().isOk());

        JsonNode shown = view(vet, horse);
        assertThat(shown.get("current").get("meals").get(0).get("items").get(0).get("active").asBoolean()).isFalse();
        JsonNode refused = save(vet, horse, ration(meal("Sáng", "06:00", item("GRAIN", oats, 1), item("HAY", hay, 3))), 400);
        assertThat(refused.get("fields").toString()).contains("supplyItemId").contains("Yến mạch cũ");
        assertThat(save(vet, horse, ration(meal("Sáng", "06:00", item("HAY", hay, 3))), 200)
                .get("current").get("version").asInt()).isEqualTo(2);
        JsonNode history = view(vet, horse).get("history");
        assertThat(history.get(1).get("meals").get(0).get("items").get(0).get("active").asBoolean()).isFalse();
    }

    @Test
    void AC_253_anArchivedHorseRationCannotBeChangedButStaysReadable() throws Exception {
        long horse = newHorse();
        save(vet, horse, basic(), 200);
        asJson(manager, post("/api/horses/" + horse + "/archive"), Map.of("reason", "SOLD")).andExpect(status().isOk());
        asJson(vet, put("/api/horses/" + horse + "/ration"), basic())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
        for (AccountPrincipal viewer : List.of(vet, trainer, groom, manager)) {
            assertThat(view(viewer, horse).get("current").get("version").asInt()).isEqualTo(1);
        }
        assertThat(view(owner, horse).get("current")).isNotNull();
    }

    @Test
    void AC_254_onlyTheVeterinarianComposes() throws Exception {
        long horse = newHorse();
        save(vet, horse, basic(), 200);
        for (AccountPrincipal who : List.of(trainer, groom, manager, owner)) {
            asJson(who, put("/api/horses/" + horse + "/ration"), ration()).andExpect(status().isForbidden());
        }
        assertThat(view(vet, horse).get("history")).hasSize(1);
    }

    @Test
    void AC_255_aGroomReadsTheMealsReadOnly() throws Exception {
        long horse = newHorse();
        save(vet, horse, basic(), 200);
        JsonNode shown = view(groom, horse);
        JsonNode meals = shown.get("current").get("meals");
        assertThat(meals).hasSize(2);
        assertThat(meals.get(1).get("time").asText()).isEqualTo("18:00");
        assertThat(meals.get(1).get("items").get(0).get("type").asText()).isEqualTo("VITAMIN");
        asJson(groom, put("/api/horses/" + horse + "/ration"), basic()).andExpect(status().isForbidden());
    }

    @Test
    void AC_256_staffSeeTheCurrentVersionAndTheHistory() throws Exception {
        long horse = newHorse();
        save(vet, horse, basic(), 200);
        @SuppressWarnings("unchecked")
        Map<String, Object> v2 = ration(meal("Bữa sáng", "07:00", item("GRAIN", oats, 3)));
        save(vet, horse, v2, 200);
        for (AccountPrincipal who : List.of(trainer, vet, manager)) {
            JsonNode shown = view(who, horse);
            assertThat(shown.get("current").get("version").asInt()).isEqualTo(2);
            assertThat(shown.get("history")).hasSize(2);
        }
    }

    @Test
    void AC_257_theOwnerSeesTheCurrentRationOfAnOwnHorseWithoutHistory() throws Exception {
        long horse = newHorse();
        save(vet, horse, basic(), 200);
        @SuppressWarnings("unchecked")
        Map<String, Object> v2 = ration(meal("Bữa sáng", "07:00", item("GRAIN", oats, 3)));
        save(vet, horse, v2, 200);
        JsonNode shown = view(owner, horse);
        assertThat(shown.get("current").get("version").asInt()).isEqualTo(2);
        assertThat(shown.get("history").isNull()).isTrue();
        asJson(owner, put("/api/horses/" + horse + "/ration"), basic()).andExpect(status().isForbidden());
    }

    @Test
    void AC_258_anOwnerCannotSeeTheRationOfAnotherOwnersHorse() throws Exception {
        long horse = newHorse();
        save(vet, horse, basic(), 200);
        AccountPrincipal other = account(Role.HORSE_OWNER);
        as(other, get("/api/horses/" + horse + "/ration")).andExpect(status().isForbidden());
        assertThat(body(as(other, get("/api/rations/today"))).toString()).doesNotContain("\"horseId\":" + horse);
    }

    @Test
    void AC_259_aHorseWithoutARationShowsNoRationAndNothingIsBlocked() throws Exception {
        long horse = newHorse();
        for (AccountPrincipal who : List.of(trainer, vet, groom, manager, owner)) {
            JsonNode shown = view(who, horse);
            assertThat(shown.get("current").isNull()).isTrue();
        }
        // Nothing else is blocked: the horse can still be cared for and its health read.
        as(groom, post("/api/horses/" + horse + "/chores/WASHING/done")).andExpect(status().isCreated());
        assertThat(health(horse).get("status").asText()).isNotBlank();
    }

    @Test
    void AC_260_todaysMealsAreAvailableBesideTheFeedingChore() throws Exception {
        long horse = newHorse();
        long bare = newHorse();
        save(vet, horse, basic(), 200);
        JsonNode today = body(as(groom, get("/api/rations/today")).andExpect(status().isOk()));
        JsonNode mine = null;
        for (JsonNode t : today) {
            assertThat(t.get("horseId").asLong()).isNotEqualTo(bare);
            if (t.get("horseId").asLong() == horse) {
                mine = t;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("meals")).hasSize(2);
        assertThat(mine.get("meals").get(0).get("name").asText()).isEqualTo("Bữa sáng");
        JsonNode chores = body(as(groom, get("/api/chores")).andExpect(status().isOk()));
        assertThat(chores.toString()).contains("\"horseId\":" + horse);
    }

    @Test
    void AC_261_feedingIsStillMarkedOnceADayWithOrWithoutARation() throws Exception {
        long withRation = newHorse();
        long without = newHorse();
        save(vet, withRation, basic(), 200);
        as(groom, post("/api/horses/" + withRation + "/chores/FEEDING/done")).andExpect(status().isCreated());
        as(groom, post("/api/horses/" + withRation + "/chores/FEEDING/done")).andExpect(status().isConflict());
        as(groom, post("/api/horses/" + without + "/chores/FEEDING/done")).andExpect(status().isCreated());
        assertThat(body(as(groom, get("/api/rations/today"))).toString()).doesNotContain("\"horseId\":" + without);
    }

    @Test
    @SuppressWarnings("unchecked")
    void AC_262_savingARationChangesNoHealthLockTrainingOrSupplyQuantity() throws Exception {
        long horse = newHorse();
        setStatus(horse, "INJURED");
        JsonNode before = health(horse);
        assertThat(before.get("lock").isNull()).isFalse();
        long oatsQuantity = supplyQuantity(oats);

        save(vet, horse, ration(meal("Sáng", "06:00", item("GRAIN", oats, 5))), 200);

        JsonNode after = health(horse);
        assertThat(after.get("status").asText()).isEqualTo("INJURED");
        assertThat(after.get("lock")).isEqualTo(before.get("lock"));
        assertThat(supplyQuantity(oats)).isEqualTo(oatsQuantity);
    }

    @Test
    void theVeterinarianPicksFromActiveFeedItemsOnly() throws Exception {
        long medicine = supply(unique("Thuốc"), "MEDICINE");
        String names = body(as(vet, get("/api/rations/options")).andExpect(status().isOk())).toString();
        assertThat(names).contains("\"id\":" + oats).doesNotContain("\"id\":" + medicine);
        as(groom, get("/api/rations/options")).andExpect(status().isForbidden());
        as(owner, get("/api/rations/options")).andExpect(status().isForbidden());
    }

    private long supplyQuantity(long id) throws Exception {
        for (JsonNode i : body(as(manager, get("/api/supplies")).andExpect(status().isOk()))) {
            if (i.get("id").asLong() == id) {
                return i.get("quantity").asLong();
            }
        }
        throw new AssertionError("supply item " + id);
    }
}
