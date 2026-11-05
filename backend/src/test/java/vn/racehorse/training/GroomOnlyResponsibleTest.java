package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only an active Groom is responsible for a session; the Head Trainer is told when a Groom stops being one
 * (BR-134 to BR-136, WF-F448 to WF-F451, XDEP-017, DEC-053). */
class GroomOnlyResponsibleTest extends TrainingTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private Map<String, Object> bodyFor(AccountPrincipal responsible, int inDays) {
        Map<String, Object> s = sessionBody(today().plusDays(inDays), "GALLOP", false);
        s.put("responsiblePersonId", responsible.id());
        return s;
    }

    private long scheduleFor(long plan, AccountPrincipal responsible, int inDays) throws Exception {
        return body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), bodyFor(responsible, inDays))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> change(AccountPrincipal to, boolean ack) {
        Map<String, Object> b = new HashMap<>();
        b.put("responsiblePersonId", to.id());
        b.put("acknowledgeWarning", ack);
        return b;
    }

    private long responsible(long session) throws Exception {
        return session(session).get("responsiblePerson").get("id").asLong();
    }

    @Test
    void AC_238_onlyActiveGroomsAreOffered() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        AccountPrincipal gone = account(Role.GROOM);
        as(manager, post("/api/accounts/" + gone.id() + "/deactivate")).andExpect(status().isOk());

        List<Long> offered = StreamSupport.stream(body(as(trainer, get("/api/staff")).andExpect(status().isOk()))
                .spliterator(), false).map(n -> n.get("id").asLong()).toList();

        assertThat(offered).contains(g.id(), h.id());
        assertThat(offered).doesNotContain(vet.id(), trainer.id(), manager.id(), owner.id(), gone.id());
    }

    @Test
    void AC_239_anyoneButAnActiveGroomIsRefusedWhenSchedulingAndChanging() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal gone = account(Role.GROOM);
        as(manager, post("/api/accounts/" + gone.id() + "/deactivate")).andExpect(status().isOk());
        long plan = activePlan(eligibleHorse());
        long session = scheduleFor(plan, g, 3);

        for (AccountPrincipal other : List.of(vet, manager, trainer, owner, gone)) {
            asJson(trainer, post("/api/plans/" + plan + "/sessions"), bodyFor(other, 4))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_RESPONSIBLE"))
                    .andExpect(jsonPath("$.message").value(containsString("Groom")));
            asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(other, true))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_RESPONSIBLE"));
            assertThat(responsible(session)).isEqualTo(g.id());
        }
        assertThat(body(as(trainer, get("/api/plans/" + plan + "/sessions"))).size()).isEqualTo(1);
    }

    @Test
    void AC_240_aLegacyNonGroomAssignmentStaysShownUntilReplaced() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        long plan = activePlan(eligibleHorse());
        long session = scheduleFor(plan, g, 3);
        // As it was before DEC-053: the Veterinarian is responsible, with no acknowledgement.
        jdbc.update("update training_session set responsible_person_id = ?, assignment_ack = null where id = ?",
                vet.id(), session);

        assertThat(responsible(session)).isEqualTo(vet.id());
        assertThat(session(session).get("assignmentAck").isNull()).isTrue();
        assertThat(hasNotification(vet, "phụ trách")).isFalse();

        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(vet, true))
                .andExpect(status().isBadRequest());
        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(g, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentAck").value("UNCONFIRMED"));
        assertThat(hasNotification(g, "Bạn được giao phụ trách")).isTrue();
        assertThat(hasNotification(vet, "Bạn không còn phụ trách")).isFalse();

        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(vet, true))
                .andExpect(status().isBadRequest());
        assertThat(responsible(session)).isEqualTo(g.id());
    }

    @Test
    void AC_241_roleChangeOrDeactivationAsksTheHeadTrainerToReassign() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        long hA = eligibleHorse();
        long hB = eligibleHorse();
        long one = scheduleFor(activePlan(hA), g, 2);
        long two = scheduleFor(activePlan(hB), g, 3);
        long other = scheduleFor(activePlan(eligibleHorse()), h, 3);

        asJson(manager, put("/api/accounts/" + g.id() + "/roles"), Map.of("roles", List.of("VETERINARIAN")))
                .andExpect(status().isOk());

        String nameA = horseName(hA);
        String nameB = horseName(hB);
        assertThat(hasNotification(trainer, "Cần giao lại 2 buổi tập")).isTrue();
        assertThat(hasNotification(trainer, "\"" + nameA + "\"")).isTrue();
        assertThat(hasNotification(trainer, "\"" + nameB + "\"")).isTrue();
        assertThat(hasNotification(trainer, "đổi vai trò")).isTrue();
        assertThat(responsible(one)).isEqualTo(g.id());
        assertThat(responsible(two)).isEqualTo(g.id());
        assertThat(responsible(other)).isEqualTo(h.id());

        // Deactivation asks as well: a second Groom with one upcoming session.
        AccountPrincipal k = account(Role.GROOM);
        scheduleFor(activePlan(eligibleHorse()), k, 4);
        as(manager, post("/api/accounts/" + k.id() + "/deactivate")).andExpect(status().isOk());
        assertThat(hasNotification(trainer, "đã bị vô hiệu hóa. Cần giao lại 1 buổi tập")).isTrue();
        // The notice names the Groom.
        assertThat(hasNotification(trainer, "Groom Test " + k.username())).isTrue();
    }

    @Test
    void AC_242_noUpcomingSessionSendsNothing() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal none = account(Role.GROOM);
        long plan = activePlan(eligibleHorse());
        long session = scheduleFor(plan, g, 0);
        asJson(trainer, post("/api/sessions/" + session + "/start"), Map.of("acknowledgeWarning", true))
                .andExpect(status().isOk());
        assertThat(session(session).get("state").asText()).isEqualTo("IN_PROGRESS");

        as(manager, post("/api/accounts/" + g.id() + "/deactivate")).andExpect(status().isOk());
        as(manager, post("/api/accounts/" + none.id() + "/deactivate")).andExpect(status().isOk());

        assertThat(hasNotification(trainer, "Cần giao lại")).isFalse();
    }

    @Test
    void AC_243_reassigningStillShowsTheLateWarning() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal h = account(Role.GROOM);
        LocalDateTime start = Instant.now().atZone(CLUB).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES).plusHours(2);
        Map<String, Object> s = bodyFor(g, 0);
        s.put("date", start.toLocalDate().toString());
        s.put("startTime", start.toLocalTime().toString());
        long session = body(asJson(trainer, post("/api/plans/" + activePlan(eligibleHorse()) + "/sessions"), s)
                .andExpect(status().isCreated())).get("id").asLong();

        as(manager, post("/api/accounts/" + g.id() + "/deactivate")).andExpect(status().isOk());
        assertThat(hasNotification(trainer, "Cần giao lại 1 buổi tập")).isTrue();

        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(h, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WARNING_CONFIRMATION_REQUIRED"));
        assertThat(responsible(session)).isEqualTo(g.id());
        asJson(trainer, put("/api/sessions/" + session + "/responsible"), change(h, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentAck").value("UNCONFIRMED"));
        assertThat(responsible(session)).isEqualTo(h.id());
        assertThat(hasNotification(h, "Bạn được giao phụ trách")).isTrue();
    }

    @Test
    void AC_244_onlyTheHeadTrainerSeesTheNotice() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        AccountPrincipal otherGroom = account(Role.GROOM);
        scheduleFor(activePlan(eligibleHorse()), g, 2);
        as(manager, post("/api/accounts/" + g.id() + "/deactivate")).andExpect(status().isOk());

        assertThat(hasNotification(trainer, "Cần giao lại")).isTrue();
        for (AccountPrincipal other : List.of(vet, otherGroom, owner, manager)) {
            assertThat(hasNotification(other, "Cần giao lại")).isFalse();
        }
    }
}
