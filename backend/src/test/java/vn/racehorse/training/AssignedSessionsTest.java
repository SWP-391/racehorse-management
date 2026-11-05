package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A Groom reads the sessions assigned to them and nothing else (BR-114, BR-033, PERM-032, DEC-040). */
class AssignedSessionsTest extends TrainingTestBase {

    private long scheduleFor(long plan, AccountPrincipal responsible, int inDays, String type) throws Exception {
        Map<String, Object> s = sessionBody(today().plusDays(inDays), type, false);
        s.put("responsiblePersonId", responsible.id());
        return body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), s).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private JsonNode mySessions(AccountPrincipal groom) throws Exception {
        return body(as(groom, get("/api/my-sessions")).andExpect(status().isOk()));
    }

    private static boolean lists(JsonNode list, long session) {
        return StreamSupport.stream(list.spliterator(), false).anyMatch(n -> n.get("id").asLong() == session);
    }

    @Test
    void AC_180_assignedSessionsListedWithOnlyTheAllowedFields() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        long a = eligibleHorse();
        long b = eligibleHorse();
        long sa = scheduleFor(activePlan(a), g, 1, "TROT");
        long sb = scheduleFor(activePlan(b), g, 2, "TRIAL_RUN");
        scheduleFor(activePlan(eligibleHorse()), account(Role.GROOM), 1, "GALLOP");

        JsonNode list = mySessions(g);
        assertThat(list.size()).isEqualTo(2);
        assertThat(lists(list, sa)).isTrue();
        assertThat(lists(list, sb)).isTrue();
        // Ordered by date (newest first in this list, TECH-087), then start time (BR-120).
        assertThat(list.get(0).get("id").asLong()).isEqualTo(sb);
        JsonNode first = list.get(0);
        assertThat(first.has("horseName")).isTrue();
        assertThat(first.get("date").asText()).isNotBlank();
        assertThat(first.get("startTime").asText()).matches("\\d{2}:\\d{2}(:\\d{2})?");
        assertThat(first.get("type").asText()).isNotBlank();
        assertThat(first.get("plannedDurationMin").asInt()).isEqualTo(60);
        assertThat(first.get("state").asText()).isEqualTo("SCHEDULED");
        // Nothing beyond BR-114 and the acknowledgement state of BR-118: no plan, indicators, comments or targets.
        assertThat(first.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "horseId", "horseName", "date", "startTime", "type",
                        "plannedDurationMin", "state", "assignmentAck", "plannedEnd", "plannedEndNextDay", "endedAt");
        assertThat(first.get("assignmentAck").asText()).isEqualTo("UNCONFIRMED");
    }

    @Test
    void AC_181_reassignedSessionAppearsOnlyInTheNewList() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        long session = scheduleFor(activePlan(eligibleHorse()), g, 1, "TROT");
        assertThat(lists(mySessions(g), session)).isTrue();

        asJson(trainer, put("/api/sessions/" + session + "/responsible"), Map.of("responsiblePersonId", h.id()))
                .andExpect(status().isOk());

        assertThat(lists(mySessions(g), session)).isFalse();
        assertThat(lists(mySessions(h), session)).isTrue();
    }

    @Test
    void AC_182_nothingAssignedGivesAnEmptyList() throws Exception {
        assertThat(mySessions(account(Role.GROOM)).size()).isZero();
    }

    @Test
    void AC_183_groomCannotChangeStartEndOrCancel() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        long session = scheduleFor(activePlan(eligibleHorse()), g, 0, "TROT");
        asJson(g, put("/api/sessions/" + session + "/responsible"), Map.of("responsiblePersonId", g.id()))
                .andExpect(status().isForbidden());
        asJson(g, post("/api/sessions/" + session + "/start"), Map.of("acknowledgeWarning", true))
                .andExpect(status().isForbidden());
        asJson(g, post("/api/sessions/" + session + "/end"), indicators()).andExpect(status().isForbidden());
        asJson(g, post("/api/sessions/" + session + "/cancel"), Map.of("reason", "x")).andExpect(status().isForbidden());
        assertThat(state(session)).isEqualTo("SCHEDULED");
    }

    @Test
    void AC_184_noOtherSessionPlanIndicatorCommentOrVideo() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long ofH = scheduleFor(plan, h, 1, "TRIAL_RUN");
        scheduleFor(plan, g, 2, "TROT");

        as(g, get("/api/sessions/" + ofH)).andExpect(status().isForbidden());
        as(g, get("/api/plans/" + plan)).andExpect(status().isForbidden());
        as(g, get("/api/plans/" + plan + "/sessions")).andExpect(status().isForbidden());
        as(g, get("/api/horses/" + horse + "/plans")).andExpect(status().isForbidden());
        as(g, get("/api/horses/" + horse + "/schedule")).andExpect(status().isForbidden());
        as(g, get("/api/sessions/" + ofH + "/videos")).andExpect(status().isForbidden());
        as(g, get("/api/progress")).andExpect(status().isForbidden());
        // Other roles have no "assigned sessions" view.
        as(trainer, get("/api/my-sessions")).andExpect(status().isForbidden());
        as(owner, get("/api/my-sessions")).andExpect(status().isForbidden());
    }
}
