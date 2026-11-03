package vn.racehorse.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.racehorse.training.service.GroomAvailability;
import vn.racehorse.training.service.Unavailable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The barn-hours and leave rules behind the choice of the responsible person (BR-162, TECH-146). */
class ScheduleRulesTest extends ScheduleTestBase {

    @Autowired
    GroomAvailability availability;

    private LocalDate day() {
        return today().plusDays(45);
    }

    private Optional<Unavailable> check(long groom, String start, int minutes) {
        return availability.check(groom, day(), LocalTime.parse(start), minutes, null, -1L);
    }

    @Test
    void AC_307_aSessionOverlappingTheBarnHoursIsUnavailableWithTheHoursAsDetail() throws Exception {
        long groom = newGroom(List.of(hoursOn(day(), "05:00", "12:00")));
        Optional<Unavailable> hit = check(groom, "06:00", 60);
        assertThat(hit).isPresent();
        assertThat(hit.get().reason()).isEqualTo(Unavailable.Reason.BARN_HOURS);
        assertThat(hit.get().detail()).isEqualTo("05:00-12:00");
        assertThat(check(groom, "11:30", 60)).as("the end of the session is after the start of the hours").isPresent();
        assertThat(check(groom, "04:30", 60)).as("ends inside the hours").isPresent();
    }

    @Test
    void AC_306_aSessionStartingExactlyWhenTheHoursEndOrEndingExactlyWhenTheyStartIsFree() throws Exception {
        long groom = newGroom(List.of(hoursOn(day(), "05:00", "12:00")));
        assertThat(check(groom, "12:00", 60)).isEmpty();
        assertThat(check(groom, "04:00", 60)).isEmpty();
        assertThat(check(groom, "13:30", 90)).isEmpty();
    }

    @Test
    void aWeekdayWithoutHoursAndAGroomWithoutAnyScheduleAreFree() throws Exception {
        int other = day().plusDays(1).getDayOfWeek().getValue();
        long worksNextDayOnly = newGroom(List.of(range(other, "05:00", "12:00")));
        long noSchedule = newGroom(null);
        assertThat(check(worksNextDayOnly, "06:00", 60)).isEmpty();
        assertThat(check(noSchedule, "06:00", 60)).isEmpty();
    }

    @Test
    void aSessionCrossingMidnightIsCheckedOnEachDay() throws Exception {
        // 22:00 to 02:00 meets the hours 00:00-06:00 of the next day, not the hours of the first day.
        int next = day().plusDays(1).getDayOfWeek().getValue();
        long groom = newGroom(List.of(range(next, "00:00", "06:00")));
        Optional<Unavailable> hit = check(groom, "22:00", 240);
        assertThat(hit).isPresent();
        assertThat(hit.get().reason()).isEqualTo(Unavailable.Reason.BARN_HOURS);
        assertThat(check(groom, "21:00", 120)).as("ends at midnight, nothing falls on the next day").isEmpty();
    }

    @Test
    void AC_307_leaveBlocksTheWholeDayAndCancellingItFreesTheGroom() throws Exception {
        long groom = newGroom(null);
        long leave = body(recordLeave(manager, groom, day()).andExpect(status().isCreated())).get("id").asLong();
        Optional<Unavailable> hit = check(groom, "03:00", 30);
        assertThat(hit).isPresent();
        assertThat(hit.get().reason()).isEqualTo(Unavailable.Reason.LEAVE);
        assertThat(check(groom, "23:00", 30)).isPresent();
        as(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/leave/" + leave + "/cancel"))
                .andExpect(status().isOk());
        assertThat(check(groom, "03:00", 30)).isEmpty();
    }

    @Test
    void AC_310_thePickerOffersTheFreeGroomsAndNamesTheReasonForTheOthers() throws Exception {
        long horse = newHorse();
        long busy = newGroom(List.of(hoursOn(day(), "05:00", "12:00")));
        long onLeave = newGroom(null);
        recordLeave(manager, onLeave, day()).andExpect(status().isCreated());
        long free = newGroom(List.of(hoursOn(day(), "13:00", "18:00")));
        JsonNode result = body(as(trainer, get("/api/sessions/assignable-grooms").param("horseId", String.valueOf(horse))
                .param("date", day().toString()).param("startTime", "08:00").param("durationMin", "60"))
                .andExpect(status().isOk()));
        List<Long> available = StreamSupport.stream(result.get("available").spliterator(), false)
                .map(n -> n.get("id").asLong()).toList();
        assertThat(available).contains(free).doesNotContain(busy, onLeave);
        assertThat(reasonOf(result, busy)).isEqualTo("BARN_HOURS");
        assertThat(reasonOf(result, onLeave)).isEqualTo("LEAVE");
    }

    private static String reasonOf(JsonNode result, long groom) {
        return StreamSupport.stream(result.get("unavailable").spliterator(), false)
                .filter(u -> u.get("id").asLong() == groom).findFirst().map(u -> u.get("reason").asText()).orElse(null);
    }
}
