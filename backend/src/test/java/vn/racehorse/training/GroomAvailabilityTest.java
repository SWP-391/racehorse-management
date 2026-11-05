package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.service.GroomAvailability;
import vn.racehorse.training.service.Unavailable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who the Head Trainer may name as responsible person (BR-161, BR-162, TECH-136). The barn-hours and leave rules
 * belong to the Groom schedule change; here {@link StubAvailabilityRule} stands in for them.
 */
class GroomAvailabilityTest extends TrainingTestBase {

    @Autowired
    GroomAvailability availability;

    /** Far enough ahead that sessions of other tests never collide with the ones made here. */
    private LocalDate day() {
        return today().plusDays(60);
    }

    @AfterEach
    void clearStub() {
        StubAvailabilityRule.reset();
    }

    private JsonNode assignable(AccountPrincipal who, long horse, String time, int minutes) throws Exception {
        return body(as(who, get("/api/sessions/assignable-grooms").param("horseId", String.valueOf(horse))
                .param("date", day().toString()).param("startTime", time).param("durationMin", String.valueOf(minutes)))
                .andExpect(status().isOk()));
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> out = new ArrayList<>();
        list.forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    private JsonNode unavailable(JsonNode result, long groom) {
        return StreamSupport.stream(result.get("unavailable").spliterator(), false)
                .filter(u -> u.get("id").asLong() == groom).findFirst().orElse(null);
    }

    @Test
    void AC_306_aGroomInTheirBarnHoursIsNotOffered() throws Exception {
        AccountPrincipal busy = account(Role.GROOM);
        AccountPrincipal free = account(Role.GROOM);
        long horse = eligibleHorse();
        StubAvailabilityRule.BARN.put(busy.id(), new LocalTime[] {LocalTime.of(6, 0), LocalTime.of(8, 0)});

        JsonNode during = assignable(trainer, horse, "06:30", 60);
        assertThat(ids(during.get("available"))).contains(free.id()).doesNotContain(busy.id());
        JsonNode why = unavailable(during, busy.id());
        assertThat(why.get("reason").asText()).isEqualTo("BARN_HOURS");
        assertThat(why.get("detail").asText()).contains("giờ làm chuồng");

        // Outside those hours the same Groom is free again.
        assertThat(ids(assignable(trainer, horse, "09:00", 60).get("available"))).contains(busy.id());
    }

    @Test
    void AC_307_aGroomOnLeaveIsNotOffered() throws Exception {
        AccountPrincipal away = account(Role.GROOM);
        long horse = eligibleHorse();
        StubAvailabilityRule.LEAVE.add(away.id());
        JsonNode result = assignable(trainer, horse, "10:00", 30);
        assertThat(ids(result.get("available"))).doesNotContain(away.id());
        assertThat(unavailable(result, away.id()).get("reason").asText()).isEqualTo("LEAVE");
    }

    @Test
    void AC_308_andAC_309_aRefusedChoiceNamesTheReasonAndOverlapIsAReasonToo() throws Exception {
        AccountPrincipal busy = account(Role.GROOM);
        AccountPrincipal other = account(Role.GROOM);
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        StubAvailabilityRule.BARN.put(busy.id(), new LocalTime[] {LocalTime.of(6, 0), LocalTime.of(8, 0)});
        Map<String, Object> body = sessionBody(day(), "TROT", false);
        body.put("startTime", "06:30");
        body.put("responsiblePersonId", busy.id());
        asJson(trainer, post("/api/plans/" + plan + "/sessions"), body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROOM_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value(containsString("giờ làm chuồng")));
        assertThat(body(as(trainer, get("/api/plans/" + plan + "/sessions"))).size()).isZero();

        // A Groom who already has a session at that time is listed with the reason OVERLAP and cannot be chosen.
        body.put("responsiblePersonId", other.id());
        body.put("startTime", "10:00");
        asJson(trainer, post("/api/plans/" + plan + "/sessions"), body).andExpect(status().isCreated());
        long horse2 = eligibleHorse();
        JsonNode result = assignable(trainer, horse2, "10:30", 60);
        assertThat(unavailable(result, other.id()).get("reason").asText()).isEqualTo("OVERLAP");
        long second = activePlan(horse2);
        Map<String, Object> clash = sessionBody(day(), "TROT", false);
        clash.put("startTime", "10:30");
        clash.put("responsiblePersonId", other.id());
        asJson(trainer, post("/api/plans/" + second + "/sessions"), clash).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROOM_NOT_AVAILABLE"));
    }

    @Test
    void AC_310_whenNobodyIsFreeTheNextFreeTimeIsGiven() throws Exception {
        long horse = eligibleHorse();
        JsonNode staff = body(as(trainer, get("/api/staff")));
        for (JsonNode g : staff) {
            StubAvailabilityRule.BARN.put(g.get("id").asLong(), new LocalTime[] {LocalTime.of(6, 0), LocalTime.of(8, 0)});
        }
        JsonNode result = assignable(trainer, horse, "06:30", 60);
        assertThat(result.get("available").size()).isZero();
        assertThat(result.get("unavailable").size()).isEqualTo(staff.size());
        assertThat(result.get("nextFree").asText()).isEqualTo("08:00");
    }

    @Test
    void aRangeCrossingMidnightIsCheckedOnBothDays() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        long horse = eligibleHorse();
        StubAvailabilityRule.BARN.put(g.id(), new LocalTime[] {LocalTime.MIDNIGHT, LocalTime.of(0, 30)});
        Optional<Unavailable> hit = availability.check(g.id(), day(), LocalTime.of(23, 30), 60, null, horse);
        assertThat(hit).isPresent();
        assertThat(hit.get().reason()).isEqualTo(Unavailable.Reason.BARN_HOURS);
        // Ending exactly at midnight leaves nothing on the next day.
        assertThat(availability.check(g.id(), day(), LocalTime.of(23, 0), 60, null, horse)).isEmpty();
    }

    @Test
    void aSessionWithoutAStartTimeIsNotChecked() throws Exception {
        AccountPrincipal g = account(Role.GROOM);
        StubAvailabilityRule.BARN.put(g.id(), new LocalTime[] {LocalTime.MIDNIGHT, LocalTime.of(23, 59)});
        assertThat(availability.check(g.id(), day(), null, 60, null, eligibleHorse())).isEmpty();
    }

    @Test
    void AC_238_onlyActiveGroomsAreOffered() throws Exception {
        AccountPrincipal gone = account(Role.GROOM);
        long horse = eligibleHorse();
        as(manager, post("/api/accounts/" + gone.id() + "/deactivate")).andExpect(status().isOk());
        JsonNode result = assignable(trainer, horse, "11:00", 30);
        assertThat(ids(result.get("available"))).doesNotContain(gone.id());
        assertThat(unavailable(result, gone.id())).isNull();
        // A Head Trainer, Veterinarian or Owner is not a choice either (BR-134).
        asJson(trainer, put("/api/sessions/" + schedule(activePlan(horse), day()) + "/responsible"),
                Map.of("responsiblePersonId", vet.id())).andExpect(status().isBadRequest());
    }

    @Test
    void AC_311_onlyTheAssignedTrainerAsksWhoIsFree() throws Exception {
        long horse = eligibleHorse();
        for (AccountPrincipal who : List.of(account(Role.HEAD_TRAINER), vet, defaultGroom(), owner, manager)) {
            as(who, get("/api/sessions/assignable-grooms").param("horseId", String.valueOf(horse))
                    .param("date", day().toString()).param("startTime", "10:00").param("durationMin", "30"))
                    .andExpect(status().isForbidden());
        }
    }
}
