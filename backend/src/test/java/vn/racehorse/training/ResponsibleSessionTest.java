package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.service.SessionService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The responsible person is optional in Nháp, mandatory later, can be declined and is reminded (BR-158 to BR-160). */
class ResponsibleSessionTest extends TrainingTestBase {

    @Autowired
    SessionService sessions;

    private Map<String, Object> withoutPerson(LocalDate date, boolean draft) {
        Map<String, Object> b = sessionBody(date, "GALLOP", draft);
        b.remove("responsiblePersonId");
        return b;
    }

    private long countNotices(AccountPrincipal who, String text) throws Exception {
        JsonNode inbox = body(as(who, get("/api/notifications")));
        return StreamSupport.stream(inbox.get("items").spliterator(), false)
                .filter(n -> n.get("message").asText().contains(text)).count();
    }

    private long scheduleFor(long plan, AccountPrincipal groom, LocalDateTime start) throws Exception {
        Map<String, Object> b = sessionBody(start.toLocalDate(), "GALLOP", false);
        b.put("startTime", start.toLocalTime().withSecond(0).withNano(0).toString());
        b.put("responsiblePersonId", groom.id());
        return body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), b).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    @Test
    void AC_298_aDraftMayHaveNoResponsiblePerson() throws Exception {
        long plan = activePlan(eligibleHorse());
        JsonNode s = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"),
                withoutPerson(today().plusDays(2), true)).andExpect(status().isCreated()));
        assertThat(s.get("state").asText()).isEqualTo("DRAFT");
        assertThat(s.get("responsiblePerson").isNull()).isTrue();
        assertThat(s.get("needsResponsible").asBoolean()).isFalse();
    }

    @Test
    void AC_299_schedulingNeedsAResponsiblePerson() throws Exception {
        long plan = activePlan(eligibleHorse());
        asJson(trainer, post("/api/plans/" + plan + "/sessions"), withoutPerson(today().plusDays(2), false))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESPONSIBLE_REQUIRED"));
        long draft = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"),
                withoutPerson(today().plusDays(2), true)).andExpect(status().isCreated())).get("id").asLong();
        as(trainer, post("/api/sessions/" + draft + "/schedule")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESPONSIBLE_REQUIRED"));
        assertThat(state(draft)).isEqualTo("DRAFT");

        asJson(trainer, put("/api/sessions/" + draft + "/responsible"),
                Map.of("responsiblePersonId", defaultGroom().id())).andExpect(status().isOk());
        as(trainer, post("/api/sessions/" + draft + "/schedule")).andExpect(status().isOk());
        assertThat(state(draft)).isEqualTo("SCHEDULED");
    }

    @Test
    void AC_300_aSessionWithNobodyResponsibleCannotStart() throws Exception {
        long plan = activePlan(eligibleHorse());
        long session = scheduleFor(plan, defaultGroom(), LocalDateTime.now(CLUB).plusHours(2));
        as(defaultGroom(), org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/sessions/" + session + "/decline").contentType("application/json")
                .content("{\"reason\":\"Ốm\"}")).andExpect(status().isNoContent());

        asJson(trainer, post("/api/sessions/" + session + "/start"), Map.of("acknowledgeWarning", false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_RESPONSIBLE_PERSON"));
        assertThat(state(session)).isEqualTo("SCHEDULED");

        AccountPrincipal next = account(Role.GROOM);
        asJson(trainer, put("/api/sessions/" + session + "/responsible"), Map.of("responsiblePersonId", next.id(),
                "acknowledgeWarning", true)).andExpect(status().isOk());
        start(session, false);
        assertThat(state(session)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void AC_301_theGroomDeclinesWithAReasonAndTheTrainerIsToldAtOnce() throws Exception {
        long plan = activePlan(eligibleHorse());
        AccountPrincipal groom = account(Role.GROOM);
        long session = scheduleFor(plan, groom, LocalDateTime.now(CLUB).plusDays(3));

        asJson(groom, post("/api/sessions/" + session + "/decline"), Map.of("reason", "Bận việc gia đình"))
                .andExpect(status().isNoContent());

        JsonNode s = session(session);
        assertThat(s.get("responsiblePerson").isNull()).isTrue();
        assertThat(s.get("needsResponsible").asBoolean()).isTrue();
        assertThat(s.get("declinedBy").get("id").asLong()).isEqualTo(groom.id());
        assertThat(s.get("declineReason").asText()).isEqualTo("Bận việc gia đình");
        assertThat(s.get("assignmentAck").isNull()).isTrue();
        assertThat(hasNotification(trainer, "không nhận buổi tập")).isTrue();
        JsonNode audit = body(as(manager, get("/api/audit").param("actorId", groom.id().toString())));
        assertThat(StreamSupport.stream(audit.spliterator(), false)
                .anyMatch(e -> e.get("action").asText().equals("SESSION_ASSIGNMENT_DECLINED"))).isTrue();
        // The refusal disappears with the next assignment.
        asJson(trainer, put("/api/sessions/" + session + "/responsible"),
                Map.of("responsiblePersonId", defaultGroom().id(), "acknowledgeWarning", true))
                .andExpect(status().isOk());
        assertThat(session(session).get("needsResponsible").asBoolean()).isFalse();
        assertThat(session(session).get("declinedBy").isNull()).isTrue();
    }

    @Test
    void AC_302_declineNeedsAReasonAndTheResponsibleGroom() throws Exception {
        long plan = activePlan(eligibleHorse());
        AccountPrincipal groom = account(Role.GROOM);
        long session = scheduleFor(plan, groom, LocalDateTime.now(CLUB).plusDays(3));
        for (String blank : List.of("", "   ")) {
            asJson(groom, post("/api/sessions/" + session + "/decline"), Map.of("reason", blank))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        }
        asJson(account(Role.GROOM), post("/api/sessions/" + session + "/decline"), Map.of("reason", "x"))
                .andExpect(status().isForbidden());
        assertThat(session(session).get("responsiblePerson").get("id").asLong()).isEqualTo(groom.id());
    }

    @Test
    void AC_305_onlyAGroomDeclines() throws Exception {
        long plan = activePlan(eligibleHorse());
        long session = scheduleFor(plan, account(Role.GROOM), LocalDateTime.now(CLUB).plusDays(3));
        for (AccountPrincipal who : List.of(trainer, vet, owner, manager)) {
            asJson(who, post("/api/sessions/" + session + "/decline"), Map.of("reason", "x"))
                    .andExpect(status().isForbidden());
        }
        assertThat(session(session).get("needsResponsible").asBoolean()).isFalse();
    }

    @Test
    void AC_303_aGroomWhoHasNotAnsweredIsRemindedOnceADayAhead() throws Exception {
        long plan = activePlan(eligibleHorse());
        AccountPrincipal groom = account(Role.GROOM);
        scheduleFor(plan, groom, LocalDateTime.now(CLUB).plusHours(30));

        sessions.sendAssignmentReminders();
        assertThat(countNotices(groom, "chưa xác nhận")).isZero();

        TestClock.shift(Duration.ofHours(7)); // 23 hours before the start
        sessions.sendAssignmentReminders();
        assertThat(countNotices(groom, "chưa xác nhận")).isEqualTo(1);
        sessions.sendAssignmentReminders();
        assertThat(countNotices(groom, "chưa xác nhận")).isEqualTo(1);
        assertThat(countNotices(trainer, "vẫn chưa xác nhận")).isZero();
    }

    @Test
    void AC_304_theTrainerIsToldAtTheThresholdAndTheStartStaysAllowed() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        AccountPrincipal groom = account(Role.GROOM);
        long session = scheduleFor(plan, groom, LocalDateTime.now(CLUB).plusHours(30));

        TestClock.shift(Duration.ofHours(19)); // 11 hours before the start, inside the 12 hour threshold
        sessions.sendAssignmentReminders();
        sessions.sendAssignmentReminders();
        assertThat(countNotices(trainer, "vẫn chưa xác nhận")).isEqualTo(1);
        assertThat(countNotices(groom, "chưa xác nhận")).isEqualTo(1);

        start(session, false); // BR-119: an unconfirmed Groom only reminds, never blocks
        assertThat(state(session)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void aConfirmedAssignmentIsNeverReminded() throws Exception {
        long plan = activePlan(eligibleHorse());
        AccountPrincipal groom = account(Role.GROOM);
        long session = scheduleFor(plan, groom, LocalDateTime.now(CLUB).plusHours(30));
        as(groom, post("/api/sessions/" + session + "/acknowledge")).andExpect(status().isOk());
        TestClock.shift(Duration.ofHours(25));
        sessions.sendAssignmentReminders();
        assertThat(countNotices(groom, "chưa xác nhận")).isZero();
        assertThat(countNotices(trainer, "vẫn chưa xác nhận")).isZero();
    }
}
