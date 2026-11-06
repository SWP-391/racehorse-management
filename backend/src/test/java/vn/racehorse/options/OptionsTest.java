package vn.racehorse.options;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The choices of the search-box selects keep the permission and visibility of the list they come from (TECH-178, AC-368, AC-369). */
class OptionsTest extends TrainingTestBase {

    private JsonNode options(AccountPrincipal who, String kind, String... params) throws Exception {
        var request = get("/api/options/" + kind);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return pageBody(as(who, request).andExpect(status().isOk()));
    }

    private static boolean has(JsonNode page, long id) {
        return StreamSupport.stream(page.get("items").spliterator(), false).anyMatch(o -> o.get("id").asLong() == id);
    }

    @Test
    void AC_368_theClubManagerChoosesAnOwner_andOnlyAnActiveOwnerIsOffered() throws Exception {
        assertThat(has(options(manager, "owners", "size", "100000"), owner.id())).isTrue();
        assertThat(has(options(manager, "owners", "size", "100000"), trainer.id())).isFalse();
        as(trainer, get("/api/options/owners")).andExpect(status().isForbidden());
        as(owner, get("/api/options/owners")).andExpect(status().isForbidden());
    }

    @Test
    void anOwnerGetsNoHorseButTheirOwn_andAGroomGetsTheMinimalList() throws Exception {
        long mine = newHorse(); // owned by `owner`
        long theirs = registerHorse(manager, account(Role.HORSE_OWNER), "MALE");
        JsonNode forOwner = options(owner, "horses", "size", "100000");
        assertThat(has(forOwner, mine)).isTrue();
        assertThat(has(forOwner, theirs)).isFalse();
        JsonNode forGroom = options(account(Role.GROOM), "horses", "size", "100000");
        assertThat(has(forGroom, mine)).isTrue();
        JsonNode first = forGroom.get("items").get(0);
        assertThat(first.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "label", "hint");
    }

    @Test
    void searchIgnoresToneMarks() throws Exception {
        String name = unique("Sấm");
        long id = body(asJson(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/horses"),
                horseBody(name, "MALE", owner.id())).andExpect(status().isCreated())).get("id").asLong();
        assertThat(has(options(manager, "horses", "q", name.toUpperCase().replace("Ấ", "A")), id)).isTrue();
        assertThat(has(options(manager, "horses", "q", "khong-co-" + unique("z")), id)).isFalse();
    }

    @Test
    void AC_369_theVeterinarianPicksStaffButNeverOwners() throws Exception {
        JsonNode staff = options(vet, "staff", "size", "100000");
        assertThat(has(staff, trainer.id())).isTrue();
        assertThat(has(staff, owner.id())).isFalse();
        as(trainer, get("/api/options/staff")).andExpect(status().isForbidden());
    }

    @Test
    void theHeadTrainerChoosesAmongActiveGroomsOnly() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);
        JsonNode grooms = options(trainer, "grooms", "size", "100000");
        assertThat(has(grooms, groom.id())).isTrue();
        assertThat(has(grooms, vet.id())).isFalse();
        as(vet, get("/api/options/grooms")).andExpect(status().isForbidden());
    }

    @Test
    void anUnknownKindIsNotFound_andASizeBeyondTheLimitIsRefused() throws Exception {
        as(manager, get("/api/options/khong-co")).andExpect(status().isNotFound());
        as(manager, get("/api/options/horses").param("size", "100001")).andExpect(status().isBadRequest());
    }
}
