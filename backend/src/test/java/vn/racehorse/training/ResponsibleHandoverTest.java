package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

/** Responsible-person hand-over: notify, warn when late, confirm "Đã nhận" (BR-115 to BR-119, PERM-033, DEC-041). */
class ResponsibleHandoverTest extends TrainingTestBase {

    private long scheduleFor(long horse, AccountPrincipal responsible, int inDays) throws Exception {
        Map<String, Object> s = sessionBody(today().plusDays(inDays), "GALLOP", false);
        s.put("responsiblePersonId", responsible.id());
        return body(asJson(trainer, post("/api/plans/" + activePlan(horse) + "/sessions"), s)
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> change(AccountPrincipal to, Boolean ack) {
        Map<String, Object> b = new HashMap<>();
        b.put("responsiblePersonId", to.id());
        if (ack != null) {
            b.put("acknowledgeWarning", ack);
        }
        return b;
    }

    private String ack(long session) throws Exception {
        JsonNode a = session(session).get("assignmentAck");
        return a.isNull() ? null : a.asText();
    }

    private long responsible(long session) throws Exception {
        return session(session).get("responsiblePerson").get("id").asLong();
    }

    private List<JsonNode> audit(AccountPrincipal actor, String action, long session) throws Exception {
        JsonNode all = body(as(manager, get("/api/audit").param("actorId", actor.id().toString())));
        return StreamSupport.stream(all.spliterator(), false)
                .filter(e -> e.get("action").asText().equals(action) && e.get("targetId").asLong() == session).toList();
    }

    @Test
    void AC_185_changeTwoDaysAheadIsAuditedAndNotifiesBothGrooms() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        long horse = eligibleHorse();
        long session = scheduleFor(horse, g, 2);

        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(h, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentAck").value("UNCONFIRMED"));

        assertThat(responsible(session)).isEqualTo(h.id());
        List<JsonNode> entries = audit(trainer, "SESSION_RESPONSIBLE_CHANGED", session);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("details").asText())
                .contains("\"from\":" + g.id()).contains("\"to\":" + h.id());
        assertThat(entries.get(0).get("occurredAt").asText()).isNotBlank();
        String name = horseName(horse);
        assertThat(hasNotification(h, "Bạn được giao phụ trách buổi tập Phi của ngựa \"" + name + "\"")).isTrue();
        assertThat(hasNotification(g, "Bạn không còn phụ trách buổi tập Phi của ngựa \"" + name + "\"")).isTrue();
    }

    @Test
    void AC_186_groomConfirmsAndTheHeadTrainerSeesDaNhan() throws Exception {
        AccountPrincipal h = account(Role.GROOM);
        long session = scheduleFor(eligibleHorse(), h, 1);
        assertThat(ack(session)).isEqualTo("UNCONFIRMED");

        as(h, post("/api/sessions/" + session + "/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentAck").value("ACKNOWLEDGED"));

        assertThat(ack(session)).isEqualTo("ACKNOWLEDGED");
        assertThat(audit(h, "SESSION_ASSIGNMENT_ACKNOWLEDGED", session)).hasSize(1);
    }

    @Test
    void AC_187_firstAssignmentNotifiesAndStartsUnconfirmed() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        long horse = eligibleHorse();
        long session = scheduleFor(horse, g, 3);
        assertThat(ack(session)).isEqualTo("UNCONFIRMED");
        assertThat(hasNotification(g, "Bạn được giao phụ trách buổi tập Phi của ngựa \"" + horseName(horse) + "\""))
                .isTrue();
    }

    @Test
    void AC_188_lateChangeNeedsTheWarningConfirmed() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        // Starts in 2 hours: less than the default 12-hour threshold (BR-117, DEC-044).
        LocalDateTime start = Instant.now().atZone(CLUB).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES).plusHours(2);
        Map<String, Object> s = sessionBody(start.toLocalDate(), "GALLOP", false);
        s.put("startTime", start.toLocalTime().toString());
        s.put("responsiblePersonId", g.id());
        long session = body(asJson(trainer, post("/api/plans/" + activePlan(eligibleHorse()) + "/sessions"), s)
                .andExpect(status().isCreated())).get("id").asLong();

        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(h, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WARNING_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không kịp chuẩn bị")));
        assertThat(responsible(session)).isEqualTo(g.id());
        assertThat(hasNotification(h, "Bạn được giao phụ trách")).isFalse();
        assertThat(audit(trainer, "SESSION_RESPONSIBLE_CHANGED", session)).isEmpty();

        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(h, true))
                .andExpect(status().isOk());
        assertThat(responsible(session)).isEqualTo(h.id());
        assertThat(ack(session)).isEqualTo("UNCONFIRMED");
        assertThat(hasNotification(h, "Bạn được giao phụ trách")).isTrue();
        assertThat(hasNotification(g, "Bạn không còn phụ trách")).isTrue();
        assertThat(audit(trainer, "SESSION_RESPONSIBLE_CHANGED", session)).hasSize(1);
    }

    @Test
    void AC_189_unconfirmedStartRemindsButStarts() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        long session = scheduleFor(eligibleHorse(), g, 0);

        asJson(trainer, post("/api/sessions/" + session + "/start"), Map.of("acknowledgeWarning", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignmentAck").value("UNCONFIRMED"));
        assertThat(audit(trainer, "SESSION_STARTED", session).get(0).get("details").asText())
                .contains("\"responsibleUnconfirmed\":true");
    }

    @Test
    void AC_190_noChangeOnceStartedOrEnded() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        long running = scheduleFor(eligibleHorse(), g, 0);
        start(running, false);
        asJson(trainer, put("/api/sessions/" + running + "/responsible"), change(h, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE"));
        assertThat(responsible(running)).isEqualTo(g.id());

        end(running);
        asJson(trainer, put("/api/sessions/" + running + "/responsible"), change(h, true))
                .andExpect(status().isConflict());
        assertThat(responsible(running)).isEqualTo(g.id());
        assertThat(hasNotification(h, "Bạn được giao phụ trách")).isFalse();
    }

    @Test
    void AC_191_changingBackRestartsConfirmation() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        long session = scheduleFor(eligibleHorse(), g, 2);
        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(h, null)).andExpect(status().isOk());
        as(h, post("/api/sessions/" + session + "/acknowledge")).andExpect(status().isOk());
        assertThat(ack(session)).isEqualTo("ACKNOWLEDGED");

        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(g, null)).andExpect(status().isOk());

        assertThat(responsible(session)).isEqualTo(g.id());
        assertThat(ack(session)).isEqualTo("UNCONFIRMED");
        as(h, post("/api/sessions/" + session + "/acknowledge")).andExpect(status().isForbidden());
        assertThat(ack(session)).isEqualTo("UNCONFIRMED");
    }

    @Test
    void AC_192_onlyTheResponsibleGroomConfirmsAndOnlyOnce() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        long session = scheduleFor(eligibleHorse(), g, 1);

        as(h, post("/api/sessions/" + session + "/acknowledge")).andExpect(status().isForbidden());
        assertThat(ack(session)).isEqualTo("UNCONFIRMED");

        as(g, post("/api/sessions/" + session + "/acknowledge")).andExpect(status().isOk());
        as(g, post("/api/sessions/" + session + "/acknowledge"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ACKNOWLEDGED"));
        assertThat(audit(g, "SESSION_ASSIGNMENT_ACKNOWLEDGED", session)).hasSize(1);
        // Other roles have no "Đã nhận" action (PERM-033).
        as(trainer, post("/api/sessions/" + session + "/acknowledge")).andExpect(status().isForbidden());
    }

    @Test
    void AC_193_onlyTheHeadTrainerChangesTheResponsiblePerson() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        long session = scheduleFor(eligibleHorse(), g, 2);
        for (AccountPrincipal who : List.of(vet, h, g, owner, manager)) {
            asJson(who, put("/api/sessions/" + session + "/responsible"), change(h, true))
                    .andExpect(status().isForbidden());
        }
        assertThat(responsible(session)).isEqualTo(g.id());
        assertThat(hasNotification(h, "Bạn được giao phụ trách")).isFalse();
    }
}
