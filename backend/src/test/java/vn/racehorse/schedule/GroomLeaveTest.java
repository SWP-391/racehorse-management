package vn.racehorse.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Leave of a Groom, the "đột xuất" label, the notice to the assigned Head Trainer and the leave notice setting. */
class GroomLeaveTest extends ScheduleTestBase {

    private List<String> auditActionsOf(AccountPrincipal actor) throws Exception {
        List<String> actions = new ArrayList<>();
        for (JsonNode e : body(as(manager, get("/api/audit").param("actorId", actor.id().toString())))) {
            actions.add(e.get("action").asText());
        }
        return actions;
    }

    @Test
    void AC_322_leaveTenDaysAheadIsSavedWithoutTheLateLabel() throws Exception {
        long groom = newGroom(null);
        JsonNode leave = body(recordLeave(manager, groom, today().plusDays(10)).andExpect(status().isCreated()));
        assertThat(leave.get("late").asBoolean()).isFalse();
        assertThat(leave.get("noticeDays").asInt()).isEqualTo(10);
        assertThat(auditActionsOf(manager)).contains("LEAVE_RECORDED");
    }

    @Test
    void AC_323_leaveForTomorrowIsSavedWithTheLateLabelAndIsNotBlocked() throws Exception {
        long groom = newGroom(null);
        JsonNode leave = body(recordLeave(manager, groom, today().plusDays(1)).andExpect(status().isCreated()));
        assertThat(leave.get("late").asBoolean()).isTrue();
        JsonNode today = body(recordLeave(manager, newGroom(null), today()).andExpect(status().isCreated()));
        assertThat(today.get("late").asBoolean()).isTrue();
        assertThat(today.get("noticeDays").asInt()).isZero();
    }

    @Test
    void theLeaveNoticeSettingDecidesTheLabelAndAppliesOnlyToLaterRecords() throws Exception {
        long groom = newGroom(null);
        JsonNode before = body(recordLeave(manager, groom, today().plusDays(5)).andExpect(status().isCreated()));
        assertThat(before.get("late").asBoolean()).isTrue();
        asJson(manager, put("/api/settings/leave-notice"), Map.of("days", 3)).andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(3));
        try {
            JsonNode after = body(recordLeave(manager, groom, today().plusDays(6)).andExpect(status().isCreated()));
            assertThat(after.get("late").asBoolean()).isFalse();
            JsonNode earlier = body(as(manager, get("/api/accounts/" + groom + "/leave")));
            assertThat(earlier.get(0).get("late").asBoolean()).as("the earlier record keeps its label").isTrue();
        } finally {
            asJson(manager, put("/api/settings/leave-notice"), Map.of("days", 7)).andExpect(status().isOk());
        }
        assertThat(auditActionsOf(manager)).contains("SETTING_CHANGED");
    }

    @Test
    void leaveIsPagedByTwentyAndSearchedWithoutToneMarks() throws Exception {
        long groom = newGroom(null);
        for (int i = 1; i <= 21; i++) {
            asJson(manager, post("/api/accounts/" + groom + "/leave"),
                    Map.of("date", today().plusDays(10 + i).toString(), "reason", i == 21 ? "Đám cưới" : "Nghỉ phép")).andExpect(status().isCreated());
        }
        JsonNode first = pageBody(as(manager, get("/api/accounts/" + groom + "/leave").param("size", "20")).andExpect(status().isOk()));
        assertThat(first.get("items").size()).isEqualTo(20);
        assertThat(first.get("total").asInt()).isEqualTo(21);
        JsonNode second = pageBody(as(manager, get("/api/accounts/" + groom + "/leave").param("size", "20").param("page", "1")));
        assertThat(second.get("items").size()).isEqualTo(1);
        JsonNode found = pageBody(as(manager, get("/api/accounts/" + groom + "/leave").param("q", "dam cuoi")));
        assertThat(found.get("total").asInt()).isEqualTo(1);
    }

    @Test
    void theLeaveNoticeIsAPositiveWholeNumberSetOnlyByTheClubManager() throws Exception {
        for (Object bad : new Object[]{0, -2, new BigDecimal("1.5"), "abc"}) {
            asJson(manager, put("/api/settings/leave-notice"), Map.of("days", bad)).andExpect(status().is4xxClientError());
        }
        asJson(manager, put("/api/settings/leave-notice"), Map.of("days", 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_NOTICE"));
        for (AccountPrincipal who : List.of(trainer, vet, owner, account(Role.GROOM))) {
            asJson(who, put("/api/settings/leave-notice"), Map.of("days", 5)).andExpect(status().isForbidden());
            as(who, get("/api/settings/leave-notice")).andExpect(status().isForbidden());
        }
        as(manager, get("/api/settings/leave-notice")).andExpect(status().isOk()).andExpect(jsonPath("$.days").value(7));
    }

    @Test
    void leaveInThePastOrTwiceOnTheSameDayOrForAnotherRoleIsRefused() throws Exception {
        long groom = newGroom(null);
        recordLeave(manager, groom, today().minusDays(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE_IN_PAST"));
        recordLeave(manager, groom, today().plusDays(12)).andExpect(status().isCreated());
        recordLeave(manager, groom, today().plusDays(12)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAVE_EXISTS"));
        recordLeave(manager, vet.id(), today().plusDays(12)).andExpect(status().isBadRequest());
        asJson(manager, post("/api/accounts/" + groom + "/leave"), Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void cancellingLeaveKeepsTheRecordAndFreesTheDay() throws Exception {
        long groom = newGroom(null);
        LocalDate day = today().plusDays(15);
        long id = body(recordLeave(manager, groom, day).andExpect(status().isCreated())).get("id").asLong();
        as(manager, post("/api/leave/" + id + "/cancel")).andExpect(status().isOk());
        as(manager, post("/api/leave/" + id + "/cancel")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAVE_CANCELLED"));
        JsonNode records = body(as(manager, get("/api/accounts/" + groom + "/leave")));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("cancelled").asBoolean()).isTrue();
        recordLeave(manager, groom, day).andExpect(status().isCreated());
        assertThat(auditActionsOf(manager)).contains("LEAVE_CANCELLED");
    }

    @Test
    void AC_324_leaveNotifiesTheAssignedHeadTrainerNamingTheGroomAndTheSessionsWhichStayUnchanged() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long groom = newGroom(null);
        LocalDate day = today().plusDays(30);
        List<Long> sessions = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> body = sessionBody(day, "GALLOP", false);
            body.put("responsiblePersonId", groom);
            sessions.add(this.body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), body)
                    .andExpect(status().isCreated())).get("id").asLong());
        }
        String name = accounts.findById(groom).orElseThrow().getDisplayName();
        recordLeave(manager, groom, day).andExpect(status().isCreated());
        assertThat(hasNotification(trainer, name + " nghỉ phép ngày")).isTrue();
        assertThat(hasNotification(trainer, "Cần giao lại 2 buổi tập")).isTrue();
        for (long session : sessions) {
            assertThat(session(session).get("responsiblePerson").get("id").asLong()).isEqualTo(groom);
        }
        // A day with no session of that Groom sends nothing.
        long quiet = newGroom(null);
        recordLeave(manager, quiet, day).andExpect(status().isCreated());
        assertThat(hasNotification(trainer, accounts.findById(quiet).orElseThrow().getDisplayName())).isFalse();
    }
}
