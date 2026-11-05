package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Progress board (BR-042) and the Owner's schedule view (BR-082). */
class ProgressAndScheduleTest extends TrainingTestBase {

    private JsonNode row(long horse) throws Exception {
        for (JsonNode r : body(as(trainer, get("/api/progress")).andExpect(status().isOk()))) {
            if (r.get("horseId").asLong() == horse) {
                return r;
            }
        }
        throw new AssertionError("horse not on board");
    }

    private JsonNode item(JsonNode schedule, long session) {
        for (JsonNode n : schedule) {
            if (n.get("id").asLong() == session) {
                return n;
            }
        }
        throw new AssertionError("session not in schedule");
    }

    @Test
    void AC_065_boardShowsStatusPlanAndRecentSessions() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long session = schedule(plan, today());
        start(session, false);
        end(session);

        JsonNode r = row(horse);
        assertThat(r.get("status").asText()).isEqualTo("ELIGIBLE");
        assertThat(r.get("currentPlan").get("id").asLong()).isEqualTo(plan);
        assertThat(r.get("recentSessions").get(0).get("id").asLong()).isEqualTo(session);
        assertThat(r.get("trend").get(0).get("form").asInt()).isEqualTo(4);
    }

    @Test
    void AC_068_ownerCannotOpenBoard() throws Exception {
        as(owner, get("/api/progress")).andExpect(status().isForbidden());
    }

    @Test
    void AC_113_ownerSeesSessionsAndComments() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long done = schedule(plan, today());
        long upcoming = schedule(plan, today().plusDays(2));
        start(done, false);
        end(done);

        JsonNode schedule = body(as(owner, get("/api/horses/" + horse + "/schedule")).andExpect(status().isOk()));
        assertThat(item(schedule, done).get("comment").asText()).isEqualTo("Phản xạ tốt");
        assertThat(item(schedule, done).get("state").asText()).isEqualTo("COMPLETED");
        assertThat(item(schedule, upcoming).get("state").asText()).isEqualTo("SCHEDULED");
    }

    @Test
    void AC_114_ownerSeesBlockedSessions() throws Exception {
        long horse = observedHorse();
        long session = schedule(activePlan(horse), today().plusDays(1));
        lock(horse, "x");
        JsonNode schedule = body(as(owner, get("/api/horses/" + horse + "/schedule")));
        assertThat(item(schedule, session).get("state").asText()).isEqualTo("BLOCKED_BY_LOCK");
    }

    @Test
    void AC_115_otherOwnersScheduleRefused() throws Exception {
        long horse = eligibleHorse();
        as(account(Role.HORSE_OWNER), get("/api/horses/" + horse + "/schedule")).andExpect(status().isForbidden());
    }

    @Test
    void AC_116_ownerCannotEditComment() throws Exception {
        long horse = eligibleHorse();
        long session = schedule(activePlan(horse), today());
        start(session, false);
        end(session);
        Map<String, Object> edit = indicators();
        edit.put("comment", "chủ sửa");
        asJson(owner, put("/api/sessions/" + session + "/indicators"), edit).andExpect(status().isForbidden());
        asJson(owner, post("/api/sessions/" + session + "/end"), edit).andExpect(status().isForbidden());
        assertThat(session(session).get("comment").asText()).isEqualTo("Phản xạ tốt");
    }
}
