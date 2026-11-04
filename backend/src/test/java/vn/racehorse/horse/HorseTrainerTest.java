package vn.racehorse.horse;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;
import vn.racehorse.horse.service.HorseTrainerService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One assigned Head Trainer per horse: choose, hand over, lend, refuse bad choices (BR-152, BR-155, BR-156, PERM-045). */
class HorseTrainerTest extends TrainingTestBase {

    @Autowired
    HorseTrainerService trainers;

    private ResultActions register(Long trainerId) throws Exception {
        Map<String, Object> b = horseBody(unique("Horse"), "MALE", owner.id());
        if (trainerId == null) {
            b.remove("trainerId");
        } else {
            b.put("trainerId", trainerId);
        }
        return asJson(manager, post("/api/horses"), b);
    }

    private JsonNode horse(long id, AccountPrincipal who) throws Exception {
        return body(as(who, get("/api/horses/" + id)).andExpect(status().isOk()));
    }

    private ResultActions handOver(AccountPrincipal who, long horse, Long trainerId) throws Exception {
        return asJson(who, put("/api/horses/" + horse + "/trainer"), Map.of("trainerId", trainerId));
    }

    private boolean audited(String action, long horse) throws Exception {
        JsonNode all = body(as(manager, get("/api/audit").param("action", action)));
        return StreamSupport.stream(all.spliterator(), false)
                .anyMatch(e -> e.get("targetId").asLong() == horse);
    }

    @Test
    void AC_283_registeredWithTheChosenTrainerWhoSeesItInTheirHorses() throws Exception {
        AccountPrincipal t1 = account(Role.HEAD_TRAINER);
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        long id = body(register(t1.id()).andExpect(status().isCreated())).get("id").asLong();

        JsonNode h = horse(id, t1);
        assertThat(h.get("assignedTrainer").get("id").asLong()).isEqualTo(t1.id());
        assertThat(h.get("mainTrainer").get("id").asLong()).isEqualTo(t1.id());
        assertThat(h.get("canWrite").asBoolean()).isTrue();
        assertThat(horse(id, t2).get("canWrite").asBoolean()).isFalse();

        List<Long> mine = StreamSupport.stream(body(as(t1, get("/api/horses").param("trainerId", t1.id().toString())))
                .spliterator(), false).map(n -> n.get("id").asLong()).toList();
        assertThat(mine).contains(id);
        List<Long> theirs = StreamSupport.stream(body(as(t1, get("/api/horses").param("trainerId", t2.id().toString())))
                .spliterator(), false).map(n -> n.get("id").asLong()).toList();
        assertThat(theirs).doesNotContain(id);
    }

    @Test
    void AC_284_registrationWithoutATrainerIsRefused() throws Exception {
        register(null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRAINER_REQUIRED"))
                .andExpect(jsonPath("$.fields.trainerId").exists());
    }

    @Test
    void AC_285_handOverKeepsEverythingAndSwitchesTheRightsAndBothAreNotified() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long session = schedule(plan, today().plusDays(2));
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);

        handOver(manager, horse, t2.id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTrainer.id").value(t2.id()));

        assertThat(state(session)).isEqualTo("SCHEDULED");
        assertThat(body(as(trainer, get("/api/plans/" + plan + "/sessions"))).size()).isEqualTo(1);
        asJson(t2, post("/api/plans/" + plan + "/sessions"), sessionBody(today().plusDays(3), "TROT", true))
                .andExpect(status().isCreated());
        asJson(trainer, post("/api/plans/" + plan + "/sessions"), sessionBody(today().plusDays(4), "TROT", true))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ASSIGNED_TRAINER"));
        assertThat(audited("HORSE_TRAINER_CHANGED", horse)).isTrue();
        assertThat(hasNotification(trainer, "không còn do bạn")).isTrue();
        assertThat(hasNotification(t2, "được giao cho bạn")).isTrue();
    }

    @Test
    void AC_286_loanGivesTheRightsAndTheHorseComesBackOnTheReturnDate() throws Exception {
        long horse = eligibleHorse();
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        LocalDate until = today().plusDays(2);
        asJson(manager, put("/api/horses/" + horse + "/trainer/temporary"),
                Map.of("trainerId", t2.id(), "until", until.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTrainer.id").value(t2.id()))
                .andExpect(jsonPath("$.mainTrainer.id").value(trainer.id()))
                .andExpect(jsonPath("$.temporaryTrainer.until").value(until.toString()));

        Map<String, Object> plan = Map.of("title", "Giáo án tạm", "phases",
                List.of(phase("Nền tảng", today(), today().plusDays(30))));
        asJson(t2, post("/api/horses/" + horse + "/plans"), plan).andExpect(status().isCreated());
        asJson(trainer, post("/api/horses/" + horse + "/plans"), plan).andExpect(status().isForbidden());
        assertThat(audited("HORSE_TEMP_TRAINER_SET", horse)).isTrue();
        assertThat(hasNotification(t2, "phụ trách tạm")).isTrue();

        // On the return date the rights switch at once; the job then clears the loan once, audited and announced.
        TestClock.shift(Duration.ofDays(2));
        assertThat(horse(horse, trainer).get("canWrite").asBoolean()).isTrue();
        assertThat(horse(horse, t2).get("canWrite").asBoolean()).isFalse();
        assertThat(trainers.endExpiredLoans()).isGreaterThanOrEqualTo(1);
        assertThat(trainers.endExpiredLoans()).isZero();
        assertThat(horse(horse, trainer).get("temporaryTrainer").isNull()).isTrue();
        assertThat(audited("HORSE_TEMP_TRAINER_ENDED", horse)).isTrue();
        assertThat(hasNotification(t2, "đã trả về")).isTrue();
        assertThat(hasNotification(trainer, "đã trở lại do bạn")).isTrue();
    }

    @Test
    void AC_286_aLoanCanEndEarlyAndNeedsAnotherActiveTrainerAndAFutureDate() throws Exception {
        long horse = eligibleHorse();
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        String future = today().plusDays(3).toString();
        asJson(manager, put("/api/horses/" + horse + "/trainer/temporary"),
                Map.of("trainerId", trainer.id(), "until", future)).andExpect(status().isBadRequest());
        asJson(manager, put("/api/horses/" + horse + "/trainer/temporary"),
                Map.of("trainerId", t2.id(), "until", today().toString())).andExpect(status().isBadRequest());
        asJson(manager, put("/api/horses/" + horse + "/trainer/temporary"),
                Map.of("trainerId", t2.id(), "until", future)).andExpect(status().isOk());
        as(manager, delete("/api/horses/" + horse + "/trainer/temporary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTrainer.id").value(trainer.id()))
                .andExpect(jsonPath("$.temporaryTrainer").doesNotExist());
        assertThat(audited("HORSE_TEMP_TRAINER_ENDED", horse)).isTrue();
    }

    @Test
    void AC_287_onlyAnActiveHeadTrainerCanBeChosen() throws Exception {
        long horse = eligibleHorse();
        AccountPrincipal gone = account(Role.HEAD_TRAINER);
        as(manager, post("/api/accounts/" + gone.id() + "/deactivate")).andExpect(status().isOk());
        for (AccountPrincipal bad : List.of(vet, defaultGroom(), owner, gone)) {
            handOver(manager, horse, bad.id()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TRAINER"));
            register(bad.id()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TRAINER"));
        }
        assertThat(horse(horse, manager).get("assignedTrainer").get("id").asLong()).isEqualTo(trainer.id());
    }

    @Test
    void AC_288_onlyTheClubManagerAssignsTheTrainer() throws Exception {
        long horse = eligibleHorse();
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        for (AccountPrincipal who : List.of(trainer, vet, defaultGroom(), owner)) {
            handOver(who, horse, t2.id()).andExpect(status().isForbidden());
            asJson(who, put("/api/horses/" + horse + "/trainer/temporary"),
                    Map.of("trainerId", t2.id(), "until", today().plusDays(2).toString()))
                    .andExpect(status().isForbidden());
            as(who, delete("/api/horses/" + horse + "/trainer/temporary")).andExpect(status().isForbidden());
        }
        assertThat(horse(horse, manager).get("assignedTrainer").get("id").asLong()).isEqualTo(trainer.id());
    }

    @Test
    void editingAHorseWithAnotherTrainerGoesThroughTheSameHandOver() throws Exception {
        long horse = eligibleHorse();
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        JsonNode h = horse(horse, manager);
        Map<String, Object> edit = horseBody(h.get("name").asText(), "MALE",
                owner.id());
        edit.put("trainerId", t2.id());
        asJson(manager, put("/api/horses/" + horse), edit).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTrainer.id").value(t2.id()));
        assertThat(audited("HORSE_TRAINER_CHANGED", horse)).isTrue();
    }
}
