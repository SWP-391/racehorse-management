package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.security.AccountPrincipal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The catalog of stable areas (BR-163, BR-164, PERM-046). */
class AreasTest extends StableTestBase {

    private JsonNode area(AccountPrincipal who, long id) throws Exception {
        for (JsonNode a : body(as(who, get("/api/areas")).andExpect(status().isOk()))) {
            if (a.get("id").asLong() == id) return a;
        }
        return null;
    }

    private org.springframework.test.web.servlet.ResultActions edit(long id, String name, int capacity) throws Exception {
        return asJson(manager, put("/api/areas/" + id), Map.of("name", name, "capacity", capacity));
    }

    @Test
    void AC_312_creatingAnAreaShowsAllItsStallsEmptyAndIsAudited() throws Exception {
        String name = unique("Khu A");
        long id = newArea(name, 6);
        JsonNode created = area(manager, id);
        assertThat(created.get("name").asText()).isEqualTo(name);
        assertThat(created.get("capacity").asInt()).isEqualTo(6);
        assertThat(created.get("occupied").asInt()).isZero();
        JsonNode barn = pageBody(as(manager, get("/api/barn")));
        for (JsonNode a : barn.get("areas")) {
            if (a.get("id").asLong() == id) {
                assertThat(a.get("slots")).hasSize(6);
                a.get("slots").forEach(s -> assertThat(s.get("horse").isNull()).isTrue());
            }
        }
        assertThat(auditActions(manager)).contains("AREA_CREATED");
    }

    @Test
    void theNameIsUniqueIgnoringCaseAndTheValuesAreValidated() throws Exception {
        String name = unique("Khu Duy Nhất");
        newArea(name, 6);
        asJson(manager, post("/api/areas"), Map.of("name", name.toUpperCase(), "capacity", 4))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_AREA"));
        for (int bad : new int[]{0, -1, 201}) {
            asJson(manager, post("/api/areas"), Map.of("name", unique("Khu"), "capacity", bad))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.capacity").exists());
        }
        asJson(manager, post("/api/areas"), Map.of("name", "   ", "capacity", 6)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
        asJson(manager, post("/api/areas"), Map.of("name", "x".repeat(101), "capacity", 6))
                .andExpect(status().isBadRequest());
        asJson(manager, post("/api/areas"), Map.of("name", unique("Khu"))).andExpect(status().isBadRequest());
    }

    @Test
    void AC_315_theCapacityCannotDropBelowTheHighestOccupiedStall() throws Exception {
        long id = newArea(unique("Khu A"), 6);
        place(manager, newHorse(), id, 5).andExpect(status().isOk());
        edit(id, area(manager, id).get("name").asText(), 4).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_OCCUPIED"))
                .andExpect(jsonPath("$.message").value(containsString("5")));
        assertThat(area(manager, id).get("capacity").asInt()).isEqualTo(6);
        edit(id, area(manager, id).get("name").asText(), 5).andExpect(status().isOk());
        assertThat(auditActions(manager)).contains("AREA_RESIZED");
    }

    @Test
    void AC_437_aHorseOwnerIsOfferedOnlyTheAreasThatHoldAHorseOfTheirs() throws Exception {
        long mine = newArea(unique("Khu A"), 4);
        long other = newArea(unique("Khu B"), 4);
        long empty = newArea(unique("Khu C"), 4);
        long myHorse = newHorse(); // `owner` owns it
        long theirHorse = registerHorse(manager, account(vn.racehorse.account.entity.Role.HORSE_OWNER), "FEMALE");
        place(manager, myHorse, mine, 1).andExpect(status().isOk());
        place(manager, theirHorse, other, 1).andExpect(status().isOk());

        assertThat(area(owner, mine)).isNotNull();
        assertThat(area(owner, mine).get("occupied").asInt()).isEqualTo(1);
        assertThat(area(owner, other)).isNull();
        assertThat(area(owner, empty)).isNull();
        // The Club Manager and the staff keep all areas.
        assertThat(area(manager, other)).isNotNull();
        assertThat(area(manager, empty)).isNotNull();
        assertThat(area(groom, empty)).isNotNull();
    }

    @Test
    void AC_316_anAreaWithAHorseCannotBeDeletedAndAnEmptyOneCan() throws Exception {
        long id = newArea(unique("Khu A"), 6);
        long horse = newHorse();
        place(manager, horse, id, 1).andExpect(status().isOk());
        as(manager, delete("/api/areas/" + id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AREA_NOT_EMPTY"));
        as(manager, delete("/api/horses/" + horse + "/stall")).andExpect(status().isNoContent());
        as(manager, delete("/api/areas/" + id)).andExpect(status().isNoContent());
        assertThat(area(manager, id)).isNull();
        as(manager, delete("/api/areas/" + id)).andExpect(status().isNotFound());
        assertThat(auditActions(manager)).contains("AREA_DELETED");
    }

    @Test
    void AC_317_renamingKeepsTheHorsesInTheAreaUnderTheNewName() throws Exception {
        long id = newArea(unique("Khu A"), 6);
        long horse = newHorse();
        place(manager, horse, id, 2).andExpect(status().isOk());
        String renamed = unique("Khu Đông");
        edit(id, renamed, 6).andExpect(status().isOk());
        assertThat(area(manager, id).get("name").asText()).isEqualTo(renamed);
        assertThat(area(manager, id).get("occupied").asInt()).isEqualTo(1);
        JsonNode stall = body(place(manager, horse, id, 2).andExpect(status().isOk()));
        assertThat(stall.get("area").asText()).isEqualTo(renamed);
        edit(id, renamed.toUpperCase(), 6).andExpect(status().isOk());
        long other = newArea(unique("Khu B"), 6);
        edit(other, renamed, 6).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_AREA"));
        assertThat(auditActions(manager)).contains("AREA_RENAMED");
    }

    @Test
    void AC_318_onlyTheClubManagerManagesAreasAndEveryStaffRoleReadsThem() throws Exception {
        long id = newArea(unique("Khu A"), 6);
        for (AccountPrincipal who : List.of(trainer, vet, groom, owner)) {
            asJson(who, post("/api/areas"), Map.of("name", unique("Khu"), "capacity", 6)).andExpect(status().isForbidden());
            asJson(who, put("/api/areas/" + id), Map.of("name", unique("Khu"), "capacity", 6))
                    .andExpect(status().isForbidden());
            as(who, delete("/api/areas/" + id)).andExpect(status().isForbidden());
            as(who, get("/api/areas")).andExpect(status().isOk());
        }
        assertThat(area(manager, id)).isNotNull();
    }

    @Test
    void aResizeBelowTheStallAndAPlacementIntoItAtTheSameMomentNeverBothSucceed() throws Exception {
        long horse = newHorse();
        long id = newArea(unique("Khu A"), 6);
        String name = area(manager, id).get("name").asText();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> edit(id, name, 3).andReturn().getResponse().getStatus(),
                    () -> place(manager, horse, id, 6).andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) statuses.add(f.get());
            // Either order is valid; both succeeding would leave a horse in a stall beyond the capacity.
            assertThat(statuses).doesNotContainSequence(200, 200);
        } finally {
            pool.shutdown();
        }
        JsonNode result = area(manager, id);
        int capacity = result.get("capacity").asInt();
        for (JsonNode a : pageBody(as(manager, get("/api/barn"))).get("areas")) {
            if (a.get("id").asLong() != id) continue;
            for (JsonNode s : a.get("slots")) {
                if (!s.get("horse").isNull()) assertThat(s.get("slot").asInt()).isLessThanOrEqualTo(capacity);
            }
        }
    }
}
