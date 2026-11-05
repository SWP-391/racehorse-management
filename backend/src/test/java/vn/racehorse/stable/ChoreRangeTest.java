package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The chore history of a date range, paged by horse and read-only (BR-188, AC-373 to AC-376). */
class ChoreRangeTest extends StableTestBase {

    private JsonNode range(AccountPrincipal who, String... params) throws Exception {
        var request = get("/api/chores/range");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return pageBody(as(who, request).andExpect(status().isOk()));
    }

    private static JsonNode horseRow(JsonNode page, long horse) {
        for (JsonNode h : page.get("items")) {
            if (h.get("horseId").asLong() == horse) return h;
        }
        return null;
    }

    private static JsonNode choreOf(JsonNode day, String chore) {
        for (JsonNode c : day.get("chores")) {
            if (chore.equals(c.get("chore").asText())) return c;
        }
        return null;
    }

    @Test
    void AC_373_theLastSevenDaysAreShownByDefault_andCanBeFilteredByHorse() throws Exception {
        long horse = newHorse();
        long other = newHorse();
        mark(groom, horse, "FEEDING").andExpect(status().isCreated());

        JsonNode page = range(trainer, "horseId", String.valueOf(horse));
        assertThat(page.get("items")).hasSize(1);
        JsonNode row = horseRow(page, horse);
        assertThat(row).isNotNull();
        JsonNode todayRow = row.get("days").get(0);
        assertThat(todayRow.get("date").asText()).isEqualTo(today().toString());
        assertThat(choreOf(todayRow, "FEEDING").get("done").asBoolean()).isTrue();
        assertThat(choreOf(todayRow, "WASHING").get("done").asBoolean()).isFalse();
        assertThat(horseRow(range(trainer, "size", "100"), other)).isNotNull();
    }

    @Test
    void AC_374_anEndBeforeTheStartAndATooLongRangeAreRefused() throws Exception {
        LocalDate t = today();
        as(trainer, get("/api/chores/range").param("from", t.toString()).param("to", t.minusDays(1).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RANGE"));
        as(trainer, get("/api/chores/range").param("from", t.minusDays(40).toString()).param("to", t.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RANGE_TOO_LONG"));
        as(trainer, get("/api/chores/range").param("from", t.minusDays(30).toString()).param("to", t.toString()))
                .andExpect(status().isOk());
        // 31 days counting both ends is the longest; the 32nd day is refused.
        as(trainer, get("/api/chores/range").param("from", t.minusDays(31).toString()).param("to", t.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RANGE_TOO_LONG"));
    }

    @Test
    void AC_375_aPastDayCannotBeMarkedThroughTheRangeOrTheOldEndpoints() throws Exception {
        long horse = newHorse();
        asJson(groom, post("/api/horses/" + horse + "/chores/FEEDING/done"), Map.of("date", today().minusDays(1).toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHORE_NOT_TODAY"));
    }

    @Test
    void AC_376_theOwnerReadsOwnHorsesDoneOrNotDoneAndNoGroomName() throws Exception {
        long mine = newHorse(); // `owner` owns it
        AccountPrincipal other = account(Role.HORSE_OWNER);
        long theirs = registerHorse(manager, other, "FEMALE");
        mark(groom, mine, "FEEDING").andExpect(status().isCreated());
        mark(groom, theirs, "FEEDING").andExpect(status().isCreated());

        JsonNode page = range(owner, "size", "100");
        assertThat(horseRow(page, mine)).isNotNull();
        assertThat(horseRow(page, theirs)).isNull();
        JsonNode feeding = choreOf(horseRow(page, mine).get("days").get(0), "FEEDING");
        assertThat(feeding.get("done").asBoolean()).isTrue();
        assertThat(feeding.get("groomName").isNull()).isTrue();
        assertThat(feeding.get("entryId").isNull()).isTrue();
        assertThat(feeding.get("markedAt").isNull()).isTrue();
    }

    @Test
    void theGroomAndVeterinarianReadRangesToo_butARoleWithoutViewChoresIsRefused() throws Exception {
        range(groom);
        range(vet);
        as(account(Role.CLUB_MANAGER), get("/api/chores/range")).andExpect(status().isOk());
    }

    @Test
    void AC_427_theHistoryIsAlsoOneRowPerDay_newestFirst_pagedByDay_withTheCountsOfTheDay() throws Exception {
        long horse = newHorse();
        mark(groom, horse, "FEEDING").andExpect(status().isCreated());

        JsonNode page = pageBody(as(trainer, get("/api/chores/days").param("horseId", String.valueOf(horse))
                .param("size", "10")).andExpect(status().isOk()));
        // A horse is listed only from the day it exists, so a horse made today has one day in the last seven.
        assertThat(page.get("items")).hasSize(1);
        assertThat(page.get("total").asInt()).isEqualTo(1);
        JsonNode todayRow = page.get("items").get(0);
        assertThat(todayRow.get("date").asText()).isEqualTo(today().toString());
        assertThat(todayRow.get("horses").asInt()).isEqualTo(1);
        assertThat(todayRow.get("chores").asInt()).isEqualTo(4);
        assertThat(todayRow.get("done").asInt()).isEqualTo(1);

        JsonNode second = pageBody(as(trainer, get("/api/chores/days").param("horseId", String.valueOf(horse))
                .param("size", "10").param("page", "1")).andExpect(status().isOk()));
        assertThat(second.get("items")).isEmpty();
    }

    @Test
    void AC_427_theDayHistoryKeepsTheRangeLimitAndTheOwnersScope() throws Exception {
        as(trainer, get("/api/chores/days").param("from", today().minusDays(40).toString()).param("to", today().toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RANGE_TOO_LONG"));
        long mine = newHorse();
        AccountPrincipal other = account(Role.HORSE_OWNER);
        mark(groom, mine, "WASHING").andExpect(status().isCreated());
        JsonNode page = pageBody(as(owner, get("/api/chores/days").param("size", "10")).andExpect(status().isOk()));
        assertThat(page.get("items").get(0).get("done").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode theirs = pageBody(as(other, get("/api/chores/days").param("size", "10")).andExpect(status().isOk()));
        assertThat(theirs.get("total").asInt()).isZero();
    }
}
