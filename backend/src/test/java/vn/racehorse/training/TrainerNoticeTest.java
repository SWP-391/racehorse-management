package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.simulation.service.SimulationService;
import vn.racehorse.training.service.SessionService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alerts and notices follow the assigned trainer of the horse (BR-157, BR-136, AC-294 to AC-297, AC-241 to AC-244). */
class TrainerNoticeTest extends TrainingTestBase {

    @Autowired
    SimulationService simulation;
    @Autowired
    SessionService sessions;
    @Autowired
    JdbcTemplate jdbc;

    private long notices(AccountPrincipal who, String text) throws Exception {
        JsonNode inbox = body(as(who, get("/api/notifications")));
        return StreamSupport.stream(inbox.get("items").spliterator(), false)
                .filter(n -> n.get("message").asText().contains(text)).count();
    }

    /** Reassignment requests that name the horse (the ones of BR-136), not the other notices about it. */
    private long reassignments(AccountPrincipal who, String quotedHorseName) throws Exception {
        JsonNode inbox = body(as(who, get("/api/notifications")));
        return StreamSupport.stream(inbox.get("items").spliterator(), false)
                .map(n -> n.get("message").asText())
                .filter(m -> m.contains("Cần giao lại") && m.contains(quotedHorseName)).count();
    }

    private void lockWith(long horse, String reason) throws Exception {
        asJson(vet, post("/api/horses/" + horse + "/lock"), Map.of("reason", reason)).andExpect(status().isOk());
    }

    @Test
    void AC_294_aThresholdAlertGoesToTheAssignedTrainerAndTheVeterinarianOnly() throws Exception {
        AccountPrincipal other = account(Role.HEAD_TRAINER);
        long horse = eligibleHorse();
        asJson(trainer, put("/api/horses/" + horse + "/thresholds"), Map.of("measure", "HEART_RATE", "limit", 100))
                .andExpect(status().isOk());
        long session = schedule(activePlan(horse), today());
        start(session, false);

        TestClock.shift(Duration.ofSeconds(120));
        simulation.evaluate();

        String name = horseName(horse);
        assertThat(notices(trainer, "vượt ngưỡng")).isGreaterThanOrEqualTo(1);
        assertThat(notices(vet, name)).isGreaterThanOrEqualTo(1);
        assertThat(notices(other, "vượt ngưỡng")).isZero();
    }

    @Test
    void AC_295_aLockNoticeAndAnOvertimeNoticeSkipOtherTrainers() throws Exception {
        AccountPrincipal other = account(Role.HEAD_TRAINER);
        long horse = observedHorse(); // a manual lock needs Cần theo dõi first (BR-083)
        String reason = unique("khóa-moi");
        lockWith(horse, reason);
        assertThat(notices(trainer, reason)).isEqualTo(1);
        assertThat(notices(other, reason)).isZero();

        long second = eligibleHorse();
        long running = schedule(activePlan(second), today());
        start(running, false);
        TestClock.shift(Duration.ofMinutes(61));
        sessions.flagOvertime();
        String name = horseName(second);
        assertThat(notices(trainer, "\"" + name + "\" đã quá giờ")).isEqualTo(1);
        assertThat(notices(other, "\"" + name + "\" đã quá giờ")).isZero();
    }

    @Test
    void AC_296_theTemporaryTrainerGetsTheNoticesWhileTheLoanLasts() throws Exception {
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        long horse = observedHorse();
        asJson(manager, put("/api/horses/" + horse + "/trainer/temporary"),
                Map.of("trainerId", t2.id(), "until", today().plusDays(3).toString())).andExpect(status().isOk());

        String during = unique("khóa-tam");
        lockWith(horse, during);
        assertThat(notices(t2, during)).isEqualTo(1);
        assertThat(notices(trainer, during)).isZero();

        as(manager, delete("/api/horses/" + horse + "/trainer/temporary")).andExpect(status().isOk());
        asJson(vet, post("/api/horses/" + horse + "/lock/lift"), Map.of("returnLevel", "RESUME_AS_IS"))
                .andExpect(status().isOk());
        String after = unique("khóa-sau");
        lockWith(horse, after);
        assertThat(notices(trainer, after)).isEqualTo(1);
        assertThat(notices(t2, after)).isZero();
    }

    @Test
    void AC_297_aHorseWithoutATrainerReportsToTheClubManagers() throws Exception {
        long horse = observedHorse();
        jdbc.update("update horse set main_trainer_id = null where id = ?", horse);
        String reason = unique("khóa-khong-hlv");
        lockWith(horse, reason);
        assertThat(notices(trainer, reason)).isZero();
        assertThat(notices(manager, "Ngựa chưa có HLV phụ trách. ")).isGreaterThanOrEqualTo(1);
        assertThat(notices(manager, reason)).isEqualTo(1);
    }

    @Test
    void AC_241_andAC_242_aGroomWhoLeavesTheTeamIsReportedToEachTrainerForTheirOwnHorsesOnly() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        long mine = eligibleHorse();
        Map<String, Object> first = sessionBody(today().plusDays(3), "GALLOP", false);
        first.put("responsiblePersonId", groom.id());
        asJson(trainer, post("/api/plans/" + activePlan(mine) + "/sessions"), first).andExpect(status().isCreated());

        useTrainer(t2);
        long theirs = observedHorse();
        setStatus(theirs, "ELIGIBLE");
        useTrainer(trainer);
        long plan2 = body(asJson(t2, post("/api/horses/" + theirs + "/plans"), Map.of("title", "T2",
                "phases", List.of(phase("N", today(), today().plusDays(20))))).andExpect(status().isCreated()))
                .get("id").asLong();
        as(t2, post("/api/plans/" + plan2 + "/activate")).andExpect(status().isOk());
        Map<String, Object> second = sessionBody(today().plusDays(4), "GALLOP", false);
        second.put("responsiblePersonId", groom.id());
        asJson(t2, post("/api/plans/" + plan2 + "/sessions"), second).andExpect(status().isCreated());

        asJson(manager, put("/api/accounts/" + groom.id() + "/roles"), Map.of("roles", List.of("VETERINARIAN")))
                .andExpect(status().isOk());

        String myName = "\"" + horseName(mine) + "\"";
        String theirName = "\"" + horseName(theirs) + "\"";
        assertThat(reassignments(trainer, myName)).isEqualTo(1);
        assertThat(reassignments(trainer, theirName)).isZero();
        assertThat(reassignments(t2, theirName)).isEqualTo(1);
        assertThat(reassignments(t2, myName)).isZero();
    }
}
