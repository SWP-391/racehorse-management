package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The catalog of units of supplies and the limits of supply inputs (BR-194, BR-195, BR-197). */
class SupplyUnitsTest extends StableTestBase {

    private String fresh() {
        return "u" + Long.toString(System.nanoTime(), 36).replaceAll("[0-9]", "x").substring(0, 8);
    }

    private JsonNode add(String name) throws Exception {
        return body(asJson(manager, post("/api/supply-units"), Map.of("name", name)).andExpect(status().isCreated()));
    }

    private JsonNode unitNamed(String name) throws Exception {
        return StreamSupport.stream(body(as(manager, get("/api/supply-units"))).spliterator(), false)
                .filter(u -> u.get("name").asText().equals(name)).findFirst().orElseThrow();
    }

    private JsonNode itemById(vn.racehorse.security.AccountPrincipal who, long id) throws Exception {
        for (JsonNode i : body(as(who, get("/api/supplies")).andExpect(status().isOk()))) {
            if (i.get("id").asLong() == id) return i;
        }
        return null;
    }

    private Map<String, Object> item(String name, long unitId) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("category", "FEED");
        m.put("unitId", unitId);
        m.put("price", 1000);
        m.put("openingQuantity", 5);
        return m;
    }

    @Test
    void AC_388_aUnitIsAddedAndAudited() throws Exception {
        String name = fresh();
        JsonNode unit = add(name);
        assertThat(unit.get("active").asBoolean()).isTrue();
        JsonNode log = body(as(manager, get("/api/audit").param("actorId", manager.id().toString())));
        assertThat(StreamSupport.stream(log.spliterator(), false)
                .anyMatch(e -> e.get("action").asText().equals("SUPPLY_UNIT_ADDED")
                        && e.get("targetId").asLong() == unit.get("id").asLong())).isTrue();
    }

    @Test
    void AC_389_aRenameShowsOnItemsAndADeactivatedUnitIsNotOfferedButItemsKeepIt() throws Exception {
        String name = fresh();
        long unit = add(name).get("id").asLong();
        long itemId = body(asJson(manager, post("/api/supplies"), item(unique("Cỏ"), unit)).andExpect(status().isCreated()))
                .get("id").asLong();
        String renamed = fresh();
        asJson(manager, put("/api/supply-units/" + unit), Map.of("name", renamed)).andExpect(status().isOk());
        assertThat(itemById(manager, itemId).get("unit").asText()).isEqualTo(renamed);
        assertThat(unitNamed(renamed).get("itemCount").asInt()).isEqualTo(1);

        asJson(manager, post("/api/supply-units/" + unit + "/deactivate"), Map.of()).andExpect(status().isOk());
        asJson(manager, post("/api/supplies"), item(unique("Cỏ"), unit)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.unitId").exists());
        // The item keeps it, and saving the item unchanged still works.
        asJson(manager, put("/api/supplies/" + itemId), item(itemById(manager, itemId).get("name").asText(), unit))
                .andExpect(status().isOk());
        assertThat(itemById(manager, itemId).get("unit").asText()).isEqualTo(renamed);

        asJson(manager, post("/api/supply-units/" + unit + "/activate"), Map.of()).andExpect(status().isOk());
        asJson(manager, post("/api/supplies"), item(unique("Cỏ"), unit)).andExpect(status().isCreated());
    }

    @Test
    void aUsedUnitHasNoDeleteRoute() throws Exception {
        long unit = add(fresh()).get("id").asLong();
        int code = as(manager, delete("/api/supply-units/" + unit)).andReturn().getResponse().getStatus();
        assertThat(code).isIn(404, 405);
        assertThat(unitNamed(add(fresh()).get("name").asText())).isNotNull();
    }

    @Test
    void anUnknownUnitIsRefused() throws Exception {
        asJson(manager, post("/api/supplies"), item(unique("Cỏ"), 999_999_999L)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.unitId").exists());
        Map<String, Object> none = item(unique("Cỏ"), 1);
        none.remove("unitId");
        asJson(manager, post("/api/supplies"), none).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.unitId").exists());
    }

    @Test
    void AC_390_badNamesAreRefusedOnTheField() throws Exception {
        for (String bad : new String[] {"", "   ", "123", "@@", "abcdefghijklmnopqrstu", "kg!", "a_b"}) {
            asJson(manager, post("/api/supply-units"), Map.of("name", bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.name").exists());
        }
        add("Kg" + fresh().substring(0, 3));
    }

    @Test
    void AC_390_aNameThatDiffersOnlyInCaseIsADuplicate() throws Exception {
        String name = fresh();
        add(name);
        asJson(manager, post("/api/supply-units"), Map.of("name", name.toUpperCase())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void AC_391_spacesAreTrimmedAndCollapsed() throws Exception {
        String a = fresh().substring(0, 6);
        String b = fresh().substring(0, 6);
        JsonNode unit = add("  " + a + "   " + b + " ");
        assertThat(unit.get("name").asText()).isEqualTo(a + " " + b);
    }

    @Test
    void lettersWithAndWithoutAccentsAreDifferentUnits() throws Exception {
        String stem = fresh().substring(0, 4);
        add(stem + "it");
        add(stem + "ít");
    }

    @Test
    void twoSimultaneousAddsOfTheSameNameGiveOneSuccessAndOneFieldError() throws Exception {
        String name = fresh();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Integer> call = () -> asJson(manager, post("/api/supply-units"), Map.of("name", name))
                        .andReturn().getResponse().getStatus();
                results.add(pool.submit(call));
            }
            List<Integer> codes = List.of(results.get(0).get(), results.get(1).get());
            assertThat(codes).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void AC_393_onlyTheClubManagerManagesUnits() throws Exception {
        long unit = add(fresh()).get("id").asLong();
        for (Role role : new Role[] {Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER}) {
            var who = account(role);
            as(who, get("/api/supply-units")).andExpect(status().isForbidden());
            asJson(who, post("/api/supply-units"), Map.of("name", fresh())).andExpect(status().isForbidden());
            asJson(who, put("/api/supply-units/" + unit), Map.of("name", fresh())).andExpect(status().isForbidden());
            asJson(who, post("/api/supply-units/" + unit + "/deactivate"), Map.of()).andExpect(status().isForbidden());
        }
    }

    @Test
    void aPriceMustBeWholeDong() throws Exception {
        long unit = unitId("kg");
        Map<String, Object> m = item(unique("Cỏ"), unit);
        m.put("price", 12000.50);
        asJson(manager, post("/api/supplies"), m).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.price").exists());
        m.put("price", 12000);
        long id = body(asJson(manager, post("/api/supplies"), m).andExpect(status().isCreated())).get("id").asLong();
        asJson(manager, put("/api/supplies/" + id + "/price"), Map.of("price", 12000.5)).andExpect(status().isBadRequest());
        asJson(manager, put("/api/supplies/" + id + "/price"), Map.of("price", 13000)).andExpect(status().isOk());
    }

    @Test
    void AC_394_oneQuantityLimitOnEveryInput() throws Exception {
        long unit = unitId("kg");
        for (Object over : new Object[] {1_000_001, -1, 1.5}) {
            Map<String, Object> m = item(unique("Cỏ"), unit);
            m.put("openingQuantity", over);
            asJson(manager, post("/api/supplies"), m).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.openingQuantity").exists());
        }
        Map<String, Object> atLimit = item(unique("Cỏ"), unit);
        atLimit.put("openingQuantity", 1_000_000);
        long id = body(asJson(manager, post("/api/supplies"), atLimit).andExpect(status().isCreated())).get("id").asLong();
        asJson(manager, post("/api/supplies/" + id + "/receipts"), Map.of("quantity", 1, "note", "x"))
                .andExpect(status().isBadRequest());
        asJson(manager, post("/api/supplies/" + id + "/adjustments"), Map.of("newQuantity", 1_000_001, "reason", "x"))
                .andExpect(status().isBadRequest());
        asJson(manager, post("/api/supplies/" + id + "/adjustments"), Map.of("newQuantity", 10, "reason", "kiểm kê"))
                .andExpect(status().isCreated());
    }
}
