package vn.racehorse.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The fixed weekly barn schedule of a Groom (BR-166, PERM-047, PERM-048). */
class GroomScheduleTest extends ScheduleTestBase {

    private JsonNode schedule(AccountPrincipal who, long groom) throws Exception {
        return pageBody(as(who, get("/api/accounts/" + groom + "/schedule")).andExpect(status().isOk()));
    }

    @Test
    void AC_319_aGroomIsCreatedWithTheSameWeeklyHoursEveryWeek() throws Exception {
        long id = newGroom(List.of(range(1, "05:00", "12:00"), range(2, "05:00", "12:00"), range(3, "05:00", "12:00"),
                range(4, "05:00", "12:00"), range(5, "05:00", "12:00")));
        assertThat(rangesOf(schedule(manager, id))).containsExactly("1 05:00-12:00", "2 05:00-12:00", "3 05:00-12:00",
                "4 05:00-12:00", "5 05:00-12:00");
        assertThat(auditActionsOf(manager)).contains("SCHEDULE_CHANGED");
    }

    @Test
    void AC_319_aGroomCanBeCreatedWithoutAScheduleAndGetsOneLater() throws Exception {
        long id = newGroom(null);
        assertThat(schedule(manager, id).get("ranges")).isEmpty();
        setSchedule(manager, id, List.of(range(6, "13:00", "24:00"))).andExpect(status().isOk());
        assertThat(rangesOf(schedule(manager, id))).containsExactly("6 13:00-24:00");
    }

    @Test
    void AC_320_anEndNotAfterTheStartOrOverlappingRangesAreRefusedAndNothingIsSaved() throws Exception {
        long id = newGroom(List.of(range(1, "05:00", "12:00")));
        setSchedule(manager, id, List.of(range(2, "12:00", "12:00"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields['ranges[0]']").exists());
        setSchedule(manager, id, List.of(range(2, "05:00", "12:00"), range(2, "11:00", "14:00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.ranges").exists());
        setSchedule(manager, id, List.of(range(2, "5am", "12:00"))).andExpect(status().isBadRequest());
        setSchedule(manager, id, List.of(range(8, "05:00", "12:00"))).andExpect(status().isBadRequest());
        setSchedule(manager, id, List.of(range(3, "24:00", "24:00"))).andExpect(status().isBadRequest());
        assertThat(rangesOf(schedule(manager, id))).containsExactly("1 05:00-12:00");
    }

    @Test
    void AC_320_anInvalidScheduleOnCreationCreatesNoAccount() throws Exception {
        String username = unique("groom");
        asJson(manager, post("/api/accounts"), Map.of("username", username, "displayName", "X", "password", PASSWORD,
                "roles", List.of("GROOM"), "schedule", List.of(range(1, "13:00", "12:00"))))
                .andExpect(status().isBadRequest());
        assertThat(accounts.existsByUsername(username)).isFalse();
    }

    @Test
    void AC_321_twoGroomsMayShareTheSameHoursAndSeveralRangesInOneDayAreAccepted() throws Exception {
        long a = newGroom(List.of(range(1, "05:00", "12:00")));
        long b = newGroom(List.of(range(1, "05:00", "12:00"), range(1, "14:00", "18:00")));
        assertThat(rangesOf(schedule(manager, a))).hasSize(1);
        assertThat(rangesOf(schedule(manager, b))).containsExactly("1 05:00-12:00", "1 14:00-18:00");
    }

    @Test
    void aScheduleForAnotherRoleIsRefused() throws Exception {
        AccountPrincipal vetAccount = account(Role.VETERINARIAN);
        setSchedule(manager, vetAccount.id(), List.of(range(1, "05:00", "12:00"))).andExpect(status().isBadRequest());
        asJson(manager, post("/api/accounts"), Map.of("username", unique("vet"), "displayName", "X",
                "password", PASSWORD, "roles", List.of("VETERINARIAN"), "schedule", List.of(range(1, "05:00", "12:00"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.schedule").exists());
    }

    @Test
    void changingTheRoleAwayFromGroomClearsTheScheduleAndKeepsLeaveHistory() throws Exception {
        long id = newGroom(List.of(range(1, "05:00", "12:00")));
        recordLeave(manager, id, today().plusDays(20)).andExpect(status().isCreated());
        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("VETERINARIAN")))
                .andExpect(status().isOk());
        assertThat(schedule(manager, id).get("ranges")).isEmpty();
        assertThat(auditActionsOf(manager)).contains("SCHEDULE_CLEARED");
        assertThat(pageBody(as(manager, get("/api/accounts/" + id + "/leave"))).get("items").size()).isEqualTo(1);
    }

    @Test
    void AC_325_onlyTheClubManagerChangesAScheduleAndTheHeadTrainerReadsAnyAndAGroomOnlyTheirOwn() throws Exception {
        long id = newGroom(List.of(range(1, "05:00", "12:00")));
        AccountPrincipal self = groomPrincipal(id);
        AccountPrincipal otherGroom = account(Role.GROOM);
        for (AccountPrincipal who : List.of(trainer, vet, self, owner)) {
            setSchedule(who, id, List.of(range(2, "05:00", "12:00"))).andExpect(status().isForbidden());
            recordLeave(who, id, today().plusDays(20)).andExpect(status().isForbidden());
        }
        assertThat(rangesOf(schedule(trainer, id))).hasSize(1);
        assertThat(rangesOf(schedule(self, id))).hasSize(1);
        as(otherGroom, get("/api/accounts/" + id + "/schedule")).andExpect(status().isForbidden());
        as(vet, get("/api/accounts/" + id + "/schedule")).andExpect(status().isForbidden());
        as(owner, get("/api/accounts/" + id + "/schedule")).andExpect(status().isForbidden());
        assertThat(rangesOf(schedule(manager, id))).containsExactly("1 05:00-12:00");
    }

    private List<String> auditActionsOf(AccountPrincipal actor) throws Exception {
        List<String> actions = new java.util.ArrayList<>();
        for (JsonNode e : body(as(manager, get("/api/audit").param("actorId", actor.id().toString())))) {
            actions.add(e.get("action").asText());
        }
        return actions;
    }
}
