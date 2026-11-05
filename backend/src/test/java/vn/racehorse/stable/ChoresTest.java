package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Daily chores (BR-124 to BR-127, BR-133). */
class ChoresTest extends StableTestBase {

    private static final List<String> CHORES = List.of("FEEDING", "STALL_CLEANING", "WASHING", "ICE_SOAK");

    @Test
    void AC_207_markShowsGroomAndTimeOthersStayNotDoneAndIsAudited() throws Exception {
        long a = newHorse();
        JsonNode marked = body(mark(groom, a, "FEEDING").andExpect(status().isCreated()));
        assertThat(marked.get("done").asBoolean()).isTrue();
        assertThat(marked.get("groomName").asText()).isEqualTo(groom.displayName());
        assertThat(marked.get("markedAt").isNull()).isFalse();

        JsonNode row = row(day(groom, null), a);
        assertThat(chore(row, "FEEDING").get("done").asBoolean()).isTrue();
        assertThat(chore(row, "FEEDING").get("groomName").asText()).isEqualTo(groom.displayName());
        assertThat(chore(row, "FEEDING").get("entryId").asLong()).isEqualTo(marked.get("entryId").asLong());
        for (String other : List.of("STALL_CLEANING", "WASHING", "ICE_SOAK")) {
            assertThat(chore(row, other).get("done").asBoolean()).isFalse();
        }
        assertThat(auditActions(groom)).contains("CHORE_MARKED");
    }

    @Test
    void AC_208_allFourDoneShowDoneAndAHorseWithNoneShowsFourNotDone() throws Exception {
        long a = newHorse();
        long b = newHorse();
        for (String c : CHORES) {
            mark(groom, a, c).andExpect(status().isCreated());
        }
        JsonNode list = day(groom, null);
        assertThat(row(list, a).get("chores").size()).isEqualTo(4);
        for (String c : CHORES) {
            assertThat(chore(row(list, a), c).get("done").asBoolean()).as(c).isTrue();
            assertThat(chore(row(list, b), c).get("done").asBoolean()).as(c).isFalse();
        }
    }

    @Test
    void AC_209_markingTwiceIsRefusedAndTheFirstEntryIsUnchanged() throws Exception {
        long a = newHorse();
        long first = body(mark(groom, a, "FEEDING").andExpect(status().isCreated())).get("entryId").asLong();
        AccountPrincipal other = account(Role.GROOM);
        mark(other, a, "FEEDING").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHORE_ALREADY_DONE"));
        JsonNode c = chore(row(day(groom, null), a), "FEEDING");
        assertThat(c.get("entryId").asLong()).isEqualTo(first);
        assertThat(c.get("groomName").asText()).isEqualTo(groom.displayName());
    }

    @Test
    void AC_210_yesterdayAndTomorrowCannotBeMarkedOrCancelledYesterdayStaysReadable() throws Exception {
        long a = newHorse();
        LocalDate today = LocalDate.parse(day(groom, null).get("today").asText());
        for (LocalDate d : List.of(today.minusDays(1), today.plusDays(1))) {
            asJson(groom, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/horses/" + a + "/chores/FEEDING/done"), Map.of("date", d.toString()))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHORE_NOT_TODAY"));
        }
        // An entry made yesterday (clock shifted back) can no longer be cancelled today.
        vn.racehorse.common.TestClock.shift(Duration.ofDays(-1));
        long entry = body(mark(groom, a, "WASHING").andExpect(status().isCreated())).get("entryId").asLong();
        vn.racehorse.common.TestClock.reset();
        cancel(groom, entry, "nhầm").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHORE_NOT_TODAY"));

        JsonNode yesterday = day(groom, today.minusDays(1).toString());
        assertThat(chore(row(yesterday, a), "WASHING").get("done").asBoolean()).isTrue();
        assertThat(yesterday.get("date").asText()).isEqualTo(today.minusDays(1).toString());
    }

    @Test
    void AC_211_cancelWithAReasonReturnsToNotDoneCanBeMarkedAgainAndHistoryKeepsIt() throws Exception {
        long a = newHorse();
        long entry = body(mark(groom, a, "FEEDING").andExpect(status().isCreated())).get("entryId").asLong();
        JsonNode cancelled = body(cancel(groom, entry, "nhầm ngựa").andExpect(status().isOk()));
        assertThat(cancelled.get("done").asBoolean()).isFalse();
        assertThat(done(groom, a, "FEEDING")).isFalse();
        assertThat(auditActions(groom)).contains("CHORE_CANCELLED");

        long again = body(mark(groom, a, "FEEDING").andExpect(status().isCreated())).get("entryId").asLong();
        assertThat(again).isNotEqualTo(entry);

        JsonNode history = body(as(groom, get("/api/horses/" + a + "/chores/history")).andExpect(status().isOk()));
        assertThat(history.size()).isEqualTo(2);
        JsonNode first = null;
        for (JsonNode h : history) {
            if (h.get("entryId").asLong() == entry) first = h;
        }
        assertThat(first).isNotNull();
        assertThat(first.get("cancelReason").asText()).isEqualTo("nhầm ngựa");
        assertThat(first.get("cancelledAt").isNull()).isFalse();
        assertThat(first.get("groomName").asText()).isEqualTo(groom.displayName());
    }

    @Test
    void AC_212_cancelWithoutAReasonIsRefusedAndTheChoreStaysDone() throws Exception {
        long a = newHorse();
        long entry = body(mark(groom, a, "FEEDING").andExpect(status().isCreated())).get("entryId").asLong();
        cancel(groom, entry, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.fields.reason").exists());
        cancel(groom, entry, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.reason").exists());
        assertThat(done(groom, a, "FEEDING")).isTrue();
        // A cancel is a one-time thing: a second one is refused.
        cancel(groom, entry, "nhầm").andExpect(status().isOk());
        cancel(groom, entry, "nhầm").andExpect(status().isConflict());
    }

    @Test
    void AC_213_anArchivedHorseIsNotOnTodaysListCannotBeMarkedAndKeepsItsHistory() throws Exception {
        long a = newHorse();
        vn.racehorse.common.TestClock.shift(Duration.ofDays(-1));
        mark(groom, a, "FEEDING").andExpect(status().isCreated());
        vn.racehorse.common.TestClock.reset();
        archive(a);

        assertThat(row(day(groom, null), a)).isNull();
        mark(groom, a, "WASHING").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
        JsonNode history = body(as(groom, get("/api/horses/" + a + "/chores/history")).andExpect(status().isOk()));
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).get("chore").asText()).isEqualTo("FEEDING");
        // Yesterday's list still shows the archived horse with its entry (BR-127).
        JsonNode yesterday = row(day(groom, LocalDate.now(CLUB).minusDays(1).toString()), a);
        assertThat(yesterday).isNotNull();
        assertThat(chore(yesterday, "FEEDING").get("done").asBoolean()).isTrue();
    }

    @Test
    void AC_213_aMarkOfAHorseArchivedTodayCannotBeCancelled() throws Exception {
        long a = newHorse();
        long entry = body(mark(groom, a, "FEEDING").andExpect(status().isCreated())).get("entryId").asLong();
        archive(a);
        cancel(groom, entry, "nhầm").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
    }

    @Test
    void AC_214_otherRolesCannotMarkOrCancel() throws Exception {
        long a = newHorse();
        long entry = body(mark(groom, a, "FEEDING").andExpect(status().isCreated())).get("entryId").asLong();
        for (AccountPrincipal who : List.of(trainer, vet, owner, manager)) {
            mark(who, a, "WASHING").andExpect(status().isForbidden());
        }
        // Only the Club Manager may cancel a mark that is not their own (BR-170); the others are refused.
        for (AccountPrincipal who : List.of(trainer, vet, owner)) {
            cancel(who, entry, "nhầm").andExpect(status().isForbidden());
        }
        assertThat(done(groom, a, "FEEDING")).isTrue();
        assertThat(done(groom, a, "WASHING")).isFalse();
    }

    @Test
    void AC_215_trainerVeterinarianAndManagerSeeEveryHorseAndWhoDidIt() throws Exception {
        long a = newHorse();
        long b = newHorse();
        mark(groom, a, "FEEDING").andExpect(status().isCreated());
        for (AccountPrincipal who : List.of(trainer, vet, manager)) {
            JsonNode list = day(who, null);
            assertThat(row(list, a)).isNotNull();
            assertThat(row(list, b)).isNotNull();
            assertThat(chore(row(list, a), "FEEDING").get("done").asBoolean()).isTrue();
            assertThat(chore(row(list, a), "FEEDING").get("groomName").asText()).isEqualTo(groom.displayName());
            as(who, get("/api/horses/" + a + "/chores/history")).andExpect(status().isOk());
        }
    }

    @Test
    void AC_216_anOwnerSeesOnlyDoneOrNotForOwnHorsesWithoutGroomNamesOrHistory() throws Exception {
        AccountPrincipal other = account(Role.HORSE_OWNER);
        long a = registerHorse(manager, owner, "MALE");
        long c = registerHorse(manager, owner, "FEMALE");
        long b = registerHorse(manager, other, "MALE");
        mark(groom, a, "FEEDING").andExpect(status().isCreated());
        mark(groom, b, "FEEDING").andExpect(status().isCreated());

        JsonNode list = day(owner, null);
        assertThat(row(list, a)).isNotNull();
        assertThat(row(list, c)).isNotNull();
        assertThat(row(list, b)).isNull();
        JsonNode feeding = chore(row(list, a), "FEEDING");
        assertThat(feeding.get("done").asBoolean()).isTrue();
        assertThat(feeding.get("entryId").isNull()).isTrue();
        assertThat(feeding.get("groomName").isNull()).isTrue();
        assertThat(feeding.get("markedAt").isNull()).isTrue();
        assertThat(chore(row(list, c), "FEEDING").get("done").asBoolean()).isFalse();
        assertThat(list.toString()).doesNotContain(groom.displayName());

        as(owner, get("/api/horses/" + a + "/chores/history")).andExpect(status().isForbidden());
        as(other, get("/api/horses/" + a + "/chores/history")).andExpect(status().isForbidden());
        // Today only (PERM-036).
        as(owner, get("/api/chores").param("date", LocalDate.now(CLUB).minusDays(1).toString())).andExpect(status().isForbidden());
    }

    @Test
    void AC_237_markingAChoreOfAnInjuredLockedHorseChangesNeitherHealthNorLock() throws Exception {
        long a = newHorse();
        setStatus(a, "INJURED");
        JsonNode before = health(a);
        assertThat(before.get("status").asText()).isEqualTo("INJURED");
        assertThat(before.get("lock").isNull()).isFalse();

        mark(groom, a, "ICE_SOAK").andExpect(status().isCreated());
        assertThat(done(groom, a, "ICE_SOAK")).isTrue();
        JsonNode after = health(a);
        assertThat(after.get("status").asText()).isEqualTo("INJURED");
        assertThat(after.get("lock")).isEqualTo(before.get("lock"));
    }

    @Test
    void aChoreOutsideTheFourIsRefusedAndAnUnknownHorseIsNotFound() throws Exception {
        long a = newHorse();
        mark(groom, a, "GROOMING").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.chore").exists());
        mark(groom, 999999999L, "FEEDING").andExpect(status().isNotFound());
    }
}
