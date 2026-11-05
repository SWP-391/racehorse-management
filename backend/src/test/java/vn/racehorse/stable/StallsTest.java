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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stalls as area plus slot and the barn page with its empty placeholders (BR-129, BR-164, BR-165). */
class StallsTest extends StableTestBase {

    private JsonNode barn(AccountPrincipal who) throws Exception {
        return pageBody(as(who, get("/api/barn")).andExpect(status().isOk()));
    }

    private JsonNode areaOf(JsonNode barn, long area) {
        for (JsonNode a : barn.get("areas")) {
            if (a.get("id").asLong() == area) return a;
        }
        return null;
    }

    /** The row of a horse anywhere in the barn view: in a slot or among the horses without a stall. */
    private JsonNode barnRow(AccountPrincipal who, long horse) throws Exception {
        JsonNode view = barn(who);
        for (JsonNode a : view.get("areas")) {
            for (JsonNode s : a.get("slots")) {
                if (!s.get("horse").isNull() && s.get("horse").get("horseId").asLong() == horse) return s.get("horse");
            }
        }
        for (JsonNode h : view.get("unplaced")) {
            if (h.get("horseId").asLong() == horse) return h;
        }
        return null;
    }

    private boolean placed(AccountPrincipal who, long horse) throws Exception {
        for (JsonNode a : barn(who).get("areas")) {
            for (JsonNode s : a.get("slots")) {
                if (!s.get("horse").isNull() && s.get("horse").get("horseId").asLong() == horse) return true;
            }
        }
        return false;
    }

    @Test
    void AC_217_placingAHorseShowsItInTheAreaGridAndIsAudited() throws Exception {
        long horse = newHorse();
        String name = unique("Khu A");
        long area = newArea(name, 6);
        JsonNode stall = body(place(manager, horse, area, 3).andExpect(status().isOk()));
        assertThat(stall.get("area").asText()).isEqualTo(name);
        assertThat(stall.get("slot").asInt()).isEqualTo(3);
        JsonNode grid = areaOf(barn(manager), area);
        assertThat(grid.get("capacity").asInt()).isEqualTo(6);
        assertThat(grid.get("slots")).hasSize(6);
        assertThat(grid.get("slots").get(2).get("horse").get("horseId").asLong()).isEqualTo(horse);
        assertThat(auditActions(manager)).contains("STALL_CHANGED");
    }

    @Test
    void AC_313_theBarnViewShowsEveryStallWithEmptyPlaceholdersForTheClubManager() throws Exception {
        long a = newHorse();
        long b = newHorse();
        long area = newArea(unique("Khu A"), 6);
        place(manager, a, area, 1).andExpect(status().isOk());
        place(manager, b, area, 4).andExpect(status().isOk());
        JsonNode view = barn(manager);
        assertThat(view.get("canPlace").asBoolean()).isTrue();
        JsonNode grid = areaOf(view, area);
        long empty = 0;
        for (JsonNode s : grid.get("slots")) {
            if (s.get("horse").isNull()) empty++;
        }
        assertThat(grid.get("slots")).hasSize(6);
        assertThat(empty).isEqualTo(4);
        // Picking a horse for an empty stall places it there (the page does this with a select).
        long c = newHorse();
        place(manager, c, area, 2).andExpect(status().isOk());
        assertThat(barnRow(manager, c)).isNotNull();
        assertThat(placed(manager, c)).isTrue();
    }

    @Test
    void AC_218_aTakenSlotIsRefusedNamingTheOtherHorseAndInvalidValuesAreRefused() throws Exception {
        long a = newHorse();
        long b = newHorse();
        long area = newArea(unique("Khu A"), 6);
        place(manager, b, area, 3).andExpect(status().isOk());
        place(manager, a, area, 3).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALL_TAKEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(horseName(b))));
        place(manager, a, area, 7).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.fields.slot").exists());
        place(manager, a, area, 0).andExpect(status().isBadRequest());
        asJson(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/horses/" + a + "/stall"),
                Map.of("slot", 1)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.area").exists());
        assertThat(placed(manager, a)).isFalse();
    }

    @Test
    void AC_314_anAreaAtCapacityTakesNoFurtherHorseAndTellsTheClubManagerToCreateANewArea() throws Exception {
        long area = newArea(unique("Khu Đầy"), 2);
        place(manager, newHorse(), area, 1).andExpect(status().isOk());
        place(manager, newHorse(), area, 2).andExpect(status().isOk());
        long third = newHorse();
        place(manager, third, area, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AREA_FULL"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("tạo khu mới")));
        assertThat(placed(manager, third)).isFalse();
    }

    @Test
    void AC_219_movingOrClearingFreesTheOldStall() throws Exception {
        long a = newHorse();
        long b = newHorse();
        long c = newHorse();
        long area = newArea(unique("Khu A"), 6);
        long other = newArea(unique("Khu B"), 6);
        place(manager, a, area, 3).andExpect(status().isOk());
        place(manager, a, other, 1).andExpect(status().isOk());
        place(manager, b, area, 3).andExpect(status().isOk());
        as(manager, delete("/api/horses/" + b + "/stall")).andExpect(status().isNoContent());
        assertThat(placed(manager, b)).isFalse();
        place(manager, c, area, 3).andExpect(status().isOk());
        assertThat(placed(manager, c)).isTrue();
    }

    @Test
    void AC_220_barnGroupsHorsesByAreaWithTodaysChoresAndAHorseWithoutAStallIsListedApart() throws Exception {
        long a = newHorse();
        long none = newHorse();
        long area = newArea(unique("Khu A"), 6);
        place(manager, a, area, 1).andExpect(status().isOk());
        mark(groom, a, "FEEDING");
        mark(groom, a, "WASHING");
        for (AccountPrincipal who : List.of(trainer, vet, groom, manager)) {
            JsonNode row = barnRow(who, a);
            assertThat(placed(who, a)).isTrue();
            assertThat(row.get("choresDone").asInt()).isEqualTo(2);
            assertThat(row.get("choresTotal").asInt()).isEqualTo(4);
            assertThat(placed(who, none)).isFalse();
            assertThat(barnRow(who, none)).isNotNull();
        }
    }

    @Test
    void AC_220_aGroomGetsNameRegistrationNumberStallAndChoresAndNothingElse() throws Exception {
        long a = newHorse();
        JsonNode row = barnRow(groom, a);
        List<String> fields = new ArrayList<>();
        row.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("horseId", "horseName", "registrationNumber", "choresDone",
                "choresTotal", "legacyStall");
        // The Groom still has no profile, health or schedule endpoint (AC-106).
        as(groom, get("/api/horses/" + a)).andExpect(status().isForbidden());
        as(groom, get("/api/horses/" + a + "/health")).andExpect(status().isForbidden());
        assertThat(barn(groom).get("canPlace").asBoolean()).isFalse();
    }

    @Test
    void anOwnerSeesOnlyOwnHorsesAndNoEmptyPlaceholdersOnTheBarnPage() throws Exception {
        AccountPrincipal other = account(vn.racehorse.account.entity.Role.HORSE_OWNER);
        long mine = registerHorse(manager, owner, "MALE");
        long theirs = registerHorse(manager, other, "MALE");
        long area = newArea(unique("Khu A"), 6);
        place(manager, mine, area, 2).andExpect(status().isOk());
        place(manager, theirs, area, 5).andExpect(status().isOk());
        assertThat(barnRow(owner, mine)).isNotNull();
        assertThat(barnRow(owner, theirs)).isNull();
        JsonNode grid = areaOf(barn(owner), area);
        assertThat(grid.get("slots")).hasSize(1);
        assertThat(grid.get("slots").get(0).get("slot").asInt()).isEqualTo(2);
    }

    @Test
    void AC_221_onlyTheClubManagerChangesStalls() throws Exception {
        long a = newHorse();
        long area = newArea(unique("Khu A"), 6);
        for (AccountPrincipal who : List.of(trainer, vet, groom, owner)) {
            place(who, a, area, 1).andExpect(status().isForbidden());
            as(who, delete("/api/horses/" + a + "/stall")).andExpect(status().isForbidden());
        }
        assertThat(placed(manager, a)).isFalse();
    }

    @Test
    void AC_222_archivingFreesTheStallAndTheHorseLeavesTheBarnPage() throws Exception {
        long a = newHorse();
        long b = newHorse();
        long area = newArea(unique("Khu A"), 6);
        place(manager, a, area, 3).andExpect(status().isOk());
        archive(a);
        assertThat(barnRow(manager, a)).isNull();
        place(manager, b, area, 3).andExpect(status().isOk());
        assertThat(placed(manager, b)).isTrue();

        // Restoring does not bring the stall back, and an archived horse cannot be given one.
        as(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/horses/" + a + "/restore"))
                .andExpect(status().isOk());
        assertThat(placed(manager, a)).isFalse();
        archive(a);
        place(manager, a, area, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
    }

    @Test
    void twoPlacementsIntoOneSlotAtTheSameMomentLeaveExactlyOneHorse() throws Exception {
        long a = newHorse();
        long b = newHorse();
        long area = newArea(unique("Khu A"), 6);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> place(manager, a, area, 5).andReturn().getResponse().getStatus(),
                    () -> place(manager, b, area, 5).andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) statuses.add(f.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdown();
        }
        long inSlot = 0;
        for (JsonNode s : areaOf(barn(manager), area).get("slots")) {
            if (s.get("slot").asInt() == 5 && !s.get("horse").isNull()) inSlot++;
        }
        assertThat(inSlot).isEqualTo(1);
    }
}
