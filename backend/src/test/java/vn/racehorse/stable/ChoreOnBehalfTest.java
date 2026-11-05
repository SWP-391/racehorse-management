package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A chore marked outside the Groom's barn hours or on leave is shown to staff as "làm thay" (BR-169, AC-216). */
class ChoreOnBehalfTest extends StableTestBase {

    private void hours(AccountPrincipal who, LocalDate day, String start, String end) throws Exception {
        asJson(manager, put("/api/accounts/" + who.id() + "/schedule"), Map.of("ranges", List.of(
                Map.of("weekday", day.getDayOfWeek().getValue(), "start", start, "end", end)))).andExpect(status().isOk());
    }

    private boolean onBehalf(JsonNode marked) {
        return marked.get("onBehalf").asBoolean();
    }

    @Test
    void markingInsideTheBarnHoursIsNotOnBehalf() throws Exception {
        long horse = newHorse();
        LocalDate day = shiftTo(0, LocalTime.of(10, 0));
        hours(groom, day, "05:00", "12:00");
        JsonNode marked = body(mark(groom, horse, "FEEDING").andExpect(status().isCreated()));
        assertThat(onBehalf(marked)).isFalse();
    }

    @Test
    void markingOutsideTheBarnHoursIsOnBehalfAndStaffSeeItInTheListAndTheHistory() throws Exception {
        long horse = newHorse();
        LocalDate day = shiftTo(0, LocalTime.of(15, 0));
        hours(groom, day, "05:00", "12:00");
        JsonNode marked = body(mark(groom, horse, "WASHING").andExpect(status().isCreated()));
        assertThat(onBehalf(marked)).isTrue();
        for (AccountPrincipal who : List.of(trainer, vet, groom, manager)) {
            assertThat(chore(row(day(who, null), horse), "WASHING").get("onBehalf").asBoolean()).as(who.role().name())
                    .isTrue();
        }
        JsonNode history = body(as(manager, get("/api/horses/" + horse + "/chores/history")));
        assertThat(history.get(0).get("onBehalf").asBoolean()).isTrue();
        // The other chores of the horse are not marked, so they have no flag.
        assertThat(chore(row(day(manager, null), horse), "FEEDING").has("onBehalf")).isFalse();
    }

    @Test
    void aGroomWithoutAnyScheduleIsNeverOnBehalf() throws Exception {
        long horse = newHorse();
        shiftTo(0, LocalTime.of(3, 0));
        JsonNode marked = body(mark(groom, horse, "ICE_SOAK").andExpect(status().isCreated()));
        assertThat(onBehalf(marked)).isFalse();
    }

    @Test
    void aGroomOnLeaveTodayMarksOnBehalfEvenInsideTheirHours() throws Exception {
        long horse = newHorse();
        LocalDate day = shiftTo(0, LocalTime.of(10, 0));
        hours(groom, day, "05:00", "12:00");
        asJson(manager, post("/api/accounts/" + groom.id() + "/leave"), Map.of("date", day.toString()))
                .andExpect(status().isCreated());
        JsonNode marked = body(mark(groom, horse, "STALL_CLEANING").andExpect(status().isCreated()));
        assertThat(onBehalf(marked)).isTrue();
    }

    @Test
    void AC_216_theHorseOwnerListCarriesNoOnBehalfField() throws Exception {
        long horse = registerHorse(manager, owner, "MALE");
        LocalDate day = shiftTo(0, LocalTime.of(15, 0));
        hours(groom, day, "05:00", "12:00");
        mark(groom, horse, "FEEDING").andExpect(status().isCreated());
        JsonNode feeding = chore(row(day(owner, null), horse), "FEEDING");
        assertThat(feeding.get("done").asBoolean()).isTrue();
        assertThat(feeding.has("onBehalf")).isFalse();
        AccountPrincipal otherOwner = account(Role.HORSE_OWNER);
        assertThat(row(day(otherOwner, null), horse)).isNull();
    }
}
