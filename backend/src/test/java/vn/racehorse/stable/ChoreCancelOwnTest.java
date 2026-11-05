package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A Groom cancels only a mark they made; the Club Manager may cancel any mark of the day with a reason (BR-126, BR-170). */
class ChoreCancelOwnTest extends StableTestBase {

    private long markedBy(AccountPrincipal who, long horse, String chore) throws Exception {
        return body(mark(who, horse, chore).andExpect(status().isCreated())).get("entryId").asLong();
    }

    @Test
    void AC_327_aGroomCancelsTheirOwnMarkAndItMayBeMarkedAgain() throws Exception {
        long horse = newHorse();
        long entry = markedBy(groom, horse, "FEEDING");
        cancel(groom, entry, "nhầm ngựa").andExpect(status().isOk());
        assertThat(done(groom, horse, "FEEDING")).isFalse();
        markedBy(groom, horse, "FEEDING");
        assertThat(done(groom, horse, "FEEDING")).isTrue();
    }

    @Test
    void AC_328_anotherGroomCannotCancelItAndTheMarkStays() throws Exception {
        long horse = newHorse();
        long entry = markedBy(groom, horse, "WASHING");
        AccountPrincipal other = account(Role.GROOM);
        cancel(other, entry, "nhầm").andExpect(status().isForbidden());
        assertThat(done(groom, horse, "WASHING")).isTrue();
    }

    @Test
    void AC_329_theClubManagerCancelsAnyMarkWithAReasonAndTheHistoryNamesThem() throws Exception {
        long horse = newHorse();
        long entry = markedBy(groom, horse, "ICE_SOAK");
        cancel(manager, entry, null).andExpect(status().isBadRequest());
        cancel(manager, entry, "Groom nghỉ đột xuất").andExpect(status().isOk());
        assertThat(done(groom, horse, "ICE_SOAK")).isFalse();
        JsonNode history = body(as(manager, get("/api/horses/" + horse + "/chores/history")).andExpect(status().isOk()));
        JsonNode row = history.get(0);
        assertThat(row.get("cancelledByName").asText()).isEqualTo(manager.displayName());
        assertThat(row.get("cancelReason").asText()).isEqualTo("Groom nghỉ đột xuất");
        assertThat(row.get("groomName").asText()).isEqualTo(groom.displayName());
    }

    @Test
    void AC_330_otherRolesCannotCancelAndTheMarkCarriesTheGroomForTheClient() throws Exception {
        long horse = newHorse();
        long entry = markedBy(groom, horse, "STALL_CLEANING");
        for (AccountPrincipal who : List.of(trainer, vet, owner)) {
            cancel(who, entry, "nhầm").andExpect(status().isForbidden());
        }
        JsonNode list = day(manager, null);
        assertThat(chore(row(list, horse), "STALL_CLEANING").get("groomId").asLong()).isEqualTo(groom.id());
        // The Owner sees done or not done only, never who (BR-124).
        assertThat(chore(row(day(owner, null), horse), "STALL_CLEANING").get("groomId").isNull()).isTrue();
        as(manager, get("/api/horses/" + horse + "/chores/history")).andExpect(jsonPath("$.items[0].groomId").value(groom.id()));
    }
}
