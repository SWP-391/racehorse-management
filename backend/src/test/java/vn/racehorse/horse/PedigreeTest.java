package vn.racehorse.horse;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Two-generation pedigree and its integrity (BR-004, BR-005). */
class PedigreeTest extends ApiTest {

    private static JsonNode position(JsonNode pedigree, String position) {
        for (JsonNode n : pedigree) {
            if (n.get("position").asText().equals(position)) {
                return n;
            }
        }
        throw new AssertionError("missing position " + position);
    }

    @Test
    void AC_007_inSystemSireLinkedAndNameOnlyDam() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long foal = registerHorse(manager, owner, "MALE");
        long sire = registerHorse(manager, owner, "MALE");

        JsonNode pedigree = body(asJson(manager, put("/api/horses/" + foal + "/pedigree"), Map.of("ancestors", List.of(
                Map.of("position", "SIRE", "horseId", sire),
                Map.of("position", "DAM", "name", "Hoa Đồng Nội"))))
                .andExpect(status().isOk()));

        assertThat(position(pedigree, "SIRE").get("horse").get("id").asLong()).isEqualTo(sire);
        assertThat(position(pedigree, "DAM").get("name").asText()).isEqualTo("Hoa Đồng Nội");
        as(manager, get("/api/horses/" + sire)).andExpect(status().isOk());
    }

    @Test
    void AC_008_unknownGrandparentsAccepted() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long foal = registerHorse(manager, account(Role.HORSE_OWNER), "FEMALE");

        JsonNode pedigree = body(asJson(manager, put("/api/horses/" + foal + "/pedigree"), Map.of("ancestors", List.of(
                Map.of("position", "SIRE", "name", "Thần Phong"),
                Map.of("position", "DAM", "name", "Bạch Vân"))))
                .andExpect(status().isOk()));

        for (String grandparent : List.of("SIRE_SIRE", "SIRE_DAM", "DAM_SIRE", "DAM_DAM")) {
            JsonNode n = position(pedigree, grandparent);
            assertThat(n.get("horse").isNull()).isTrue();
            assertThat(n.get("name").isNull()).isTrue();
        }
    }

    @Test
    void AC_009_horseAsItsOwnSireOrGrandparentRefused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long a = registerHorse(manager, account(Role.HORSE_OWNER), "MALE");

        asJson(manager, put("/api/horses/" + a + "/pedigree"),
                Map.of("ancestors", List.of(Map.of("position", "SIRE", "horseId", a))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PEDIGREE_SELF"));
        asJson(manager, put("/api/horses/" + a + "/pedigree"),
                Map.of("ancestors", List.of(Map.of("position", "DAM_SIRE", "horseId", a))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PEDIGREE_SELF"));
    }

    @Test
    void AC_010_maleHorseAsDamRefused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long foal = registerHorse(manager, owner, "FEMALE");
        long male = registerHorse(manager, owner, "MALE");

        asJson(manager, put("/api/horses/" + foal + "/pedigree"),
                Map.of("ancestors", List.of(Map.of("position", "DAM", "horseId", male))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PEDIGREE_SEX"))
                .andExpect(jsonPath("$.fields['ancestors.DAM']").value("Vị trí này phải là ngựa cái."));
    }

    @Test
    void AC_011_ownerCannotChangeAnAncestor() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long foal = registerHorse(manager, owner, "MALE");

        asJson(owner, put("/api/horses/" + foal + "/pedigree"),
                Map.of("ancestors", List.of(Map.of("position", "SIRE", "name", "Đổi"))))
                .andExpect(status().isForbidden());
        as(owner, get("/api/horses/" + foal + "/pedigree")).andExpect(status().isOk());
    }
}
