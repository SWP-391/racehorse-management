package vn.racehorse.horse;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Archive and restore (BR-007). Effects on plans and sessions are verified in slice 3 (AC-012, AC-014). */
class ArchiveTest extends ApiTest {

    @Test
    void AC_013_ownerReadsArchivedHorseReadOnly() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long id = registerHorse(manager, owner, "MALE");
        asJson(manager, post("/api/horses/" + id + "/archive"), Map.of("reason", "RETIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        as(owner, get("/api/horses/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archiveReason").value("RETIRED"));
        as(owner, get("/api/horses/" + id + "/pedigree")).andExpect(status().isOk());

        JsonNode horse = body(as(manager, get("/api/horses/" + id)));
        asJson(manager, put("/api/horses/" + id),
                horseBody("Đổi tên", "MALE", owner.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
        asJson(manager, put("/api/horses/" + id + "/pedigree"),
                Map.of("ancestors", List.of(Map.of("position", "SIRE", "name", "X"))))
                .andExpect(status().isConflict());
    }

    @Test
    void AC_015_archiveWithoutReasonRefused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = registerHorse(manager, account(Role.HORSE_OWNER), "MALE");
        Map<String, Object> noReason = new HashMap<>();
        noReason.put("note", "không có lý do");
        asJson(manager, post("/api/horses/" + id + "/archive"), noReason)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARCHIVE_REASON_REQUIRED"));
        as(manager, get("/api/horses/" + id)).andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void AC_016_veterinarianCannotArchive() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = registerHorse(manager, account(Role.HORSE_OWNER), "MALE");
        asJson(account(Role.VETERINARIAN), post("/api/horses/" + id + "/archive"), Map.of("reason", "SOLD"))
                .andExpect(status().isForbidden());
        as(manager, get("/api/horses/" + id)).andExpect(jsonPath("$.archived").value(false));
    }

    /** Restore part of AC-014; "new plans can be created again" is verified in slice 3. */
    @Test
    void AC_014_restoreSucceedsAndIsAudited() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = registerHorse(manager, account(Role.HORSE_OWNER), "FEMALE");
        asJson(manager, post("/api/horses/" + id + "/archive"), Map.of("reason", "OTHER", "note", "nhầm"))
                .andExpect(status().isOk());
        as(manager, post("/api/horses/" + id + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));

        JsonNode log = body(as(manager, get("/api/audit").param("actorId", manager.id().toString())));
        List<String> actions = StreamSupport.stream(log.spliterator(), false)
                .filter(e -> e.get("targetId").asLong() == id)
                .map(e -> e.get("action").asText()).toList();
        assertThat(actions).contains("HORSE_ARCHIVED", "HORSE_RESTORED");
    }
}
