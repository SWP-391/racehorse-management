package vn.racehorse.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The read-only timetable of a Groom and of a Head Trainer (BR-200 to BR-202, PERM-056). */
class TimetableTest extends ScheduleTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode timetable(AccountPrincipal who, LocalDate from, LocalDate to) throws Exception {
        return pageBody(as(who, get("/api/my-timetable").param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk()));
    }

    private JsonNode day(JsonNode timetable, LocalDate date) {
        for (JsonNode d : timetable.get("days")) {
            if (d.get("date").asText().equals(date.toString())) {
                return d;
            }
        }
        throw new AssertionError("no day " + date);
    }

    private List<Long> ids(JsonNode day) {
        List<Long> ids = new ArrayList<>();
        day.get("items").forEach(i -> ids.add(i.get("id").asLong()));
        return ids;
    }

    /** A scheduled session of a horse on a date at a time, with the given Groom responsible. */
    private long session(long horse, LocalDate date, String time, int minutes, long groom) throws Exception {
        return sessionIn(activePlan(horse), date, time, minutes, groom);
    }

    private long sessionIn(long plan, LocalDate date, String time, int minutes, long groom) throws Exception {
        Map<String, Object> s = sessionBody(date, "TROT", false);
        s.put("startTime", time);
        s.put("plannedDurationMin", minutes);
        s.put("responsiblePersonId", groom);
        var result = asJson(trainer, post("/api/plans/" + plan + "/sessions"), s);
        assertThat(result.andReturn().getResponse().getStatus()).as(result.andReturn().getResponse().getContentAsString()).isEqualTo(201);
        return body(result).get("id").asLong();
    }

    /** Runs the fixture calls as another Head Trainer, the one who writes for the horse. */
    private <T> T actAs(AccountPrincipal who, java.util.concurrent.Callable<T> call) throws Exception {
        AccountPrincipal original = trainer;
        trainer = who;
        useTrainer(who);
        try {
            return call.call();
        } finally {
            trainer = original;
            useTrainer(original);
        }
    }

    @Test
    void AC_400_aGroomSeesBarnHoursAndTheirSessionsInTimeOrder() throws Exception {
        LocalDate day = today().plusDays(1);
        // Barn hours are the Groom's busy time: sessions are planned around them (BR-162).
        long groom = newGroom(List.of(hoursOn(day, "05:00", "09:00")));
        long h1 = eligibleHorse();
        long h2 = eligibleHorse();
        long late = session(h1, day, "14:00", 60, groom);
        long early = session(h2, day, "10:00", 60, groom);
        JsonNode d = day(timetable(groomPrincipal(groom), day, day), day);
        assertThat(ids(d)).containsExactly(early, late);
        assertThat(d.get("barnHours").get(0).get("start").asText()).isEqualTo("05:00");
        assertThat(d.get("barnHours").get(0).get("end").asText()).isEqualTo("09:00");
        assertThat(d.get("items").get(0).get("plannedEnd").asText()).startsWith("11:00");
        // A Groom's rows do not name another Groom.
        assertThat(d.get("items").get(0).get("groomName").isNull()).isTrue();
    }

    @Test
    void AC_401_aWeekShowsHoursAndALeaveDay() throws Exception {
        LocalDate monday = today().plusDays(7 - today().getDayOfWeek().getValue() + 1);
        LocalDate off = monday.plusDays(2);
        long groom = newGroom(List.of(hoursOn(monday, "05:00", "12:00"), hoursOn(off, "05:00", "12:00")));
        recordLeave(manager, groom, off).andExpect(status().isCreated());
        JsonNode week = timetable(groomPrincipal(groom), monday, monday.plusDays(6));
        assertThat(week.get("days")).hasSize(7);
        assertThat(day(week, off).get("leave").isNull()).isFalse();
        assertThat(day(week, monday).get("leave").isNull()).isTrue();
        assertThat(day(week, monday).get("barnHours")).hasSize(1);
    }

    @Test
    void severalRangesInADayAreAllShown() throws Exception {
        LocalDate day = today().plusDays(1);
        long groom = newGroom(List.of(hoursOn(day, "05:00", "09:00"), hoursOn(day, "13:00", "17:00")));
        JsonNode d = day(timetable(groomPrincipal(groom), day, day), day);
        assertThat(d.get("barnHours")).hasSize(2);
        assertThat(d.get("barnHours").get(1).get("start").asText()).isEqualTo("13:00");
    }

    @Test
    void aSessionWithoutAPlannedTimeIsListedLastAndOneEndingAfterMidnightSaysSo() throws Exception {
        LocalDate day = today().plusDays(1);
        long groom = newGroom(List.of());
        long timed = session(eligibleHorse(), day, "08:00", 60, groom);
        long untimed = session(eligibleHorse(), day, "07:00", 60, groom);
        jdbc.update("update training_session set planned_start_time = null where id = ?", untimed);
        long night = session(eligibleHorse(), day, "23:30", 60, groom);
        JsonNode d = day(timetable(groomPrincipal(groom), day, day), day);
        assertThat(ids(d)).containsExactly(timed, night, untimed);
        assertThat(d.get("items").get(2).get("startTime").isNull()).isTrue();
        assertThat(d.get("items").get(1).get("endsNextDay").asBoolean()).isTrue();
        assertThat(d.get("items").get(0).get("endsNextDay").asBoolean()).isFalse();
    }

    @Test
    void AC_402_aHeadTrainerSeesSessionsOfAssignedHorsesWithTheGroomAndNotOthers() throws Exception {
        LocalDate day = today().plusDays(1);
        long groom = newGroom(List.of());
        long mine = session(eligibleHorse(), day, "08:00", 60, groom);
        AccountPrincipal other = account(Role.HEAD_TRAINER);
        long theirs = eligibleHorse();
        asJson(manager, put("/api/horses/" + theirs + "/trainer"), Map.of("trainerId", other.id())).andExpect(status().isOk());
        actAs(other, () -> session(theirs, day, "09:00", 60, groom));
        JsonNode d = day(timetable(trainer, day, day), day);
        assertThat(ids(d)).contains(mine).hasSize(1);
        assertThat(d.get("items").get(0).get("groomName").asText()).isNotBlank();
        assertThat(d.get("barnHours")).isEmpty();
    }

    @Test
    void aTemporaryTrainerSeesTheHorseUntilTheReturnDate() throws Exception {
        long horse = eligibleHorse();
        long groom = newGroom(List.of());
        LocalDate day = today().plusDays(1);
        long plan = activePlan(horse);
        long id = sessionIn(plan, day, "08:00", 60, groom);
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        asJson(manager, put("/api/horses/" + horse + "/trainer/temporary"),
                Map.of("trainerId", t2.id(), "until", today().plusDays(3).toString())).andExpect(status().isOk());
        assertThat(ids(day(timetable(t2, day, day), day))).containsExactly(id);
        assertThat(ids(day(timetable(trainer, day, day), day))).isEmpty();
        // On and after the return date the horse is the main trainer's again.
        LocalDate back = today().plusDays(3);
        long later = actAs(t2, () -> sessionIn(plan, back, "08:00", 60, groom));
        assertThat(ids(day(timetable(trainer, back, back), back))).contains(later);
        assertThat(ids(day(timetable(t2, back, back), back))).doesNotContain(later);
    }

    @Test
    void AC_403_aReassignedOrCancelledSessionShowsAtOnce() throws Exception {
        LocalDate day = today().plusDays(1);
        long a = newGroom(List.of());
        long b = newGroom(List.of());
        long id = session(eligibleHorse(), day, "08:00", 60, a);
        assertThat(ids(day(timetable(groomPrincipal(a), day, day), day))).containsExactly(id);
        asJson(trainer, put("/api/sessions/" + id + "/responsible"), Map.of("responsiblePersonId", b))
                .andExpect(status().isOk());
        assertThat(ids(day(timetable(groomPrincipal(a), day, day), day))).isEmpty();
        assertThat(ids(day(timetable(groomPrincipal(b), day, day), day))).containsExactly(id);
        asJson(trainer, post("/api/sessions/" + id + "/cancel"), Map.of("reason", "mưa")).andExpect(status().isOk());
        JsonNode item = day(timetable(groomPrincipal(b), day, day), day).get("items").get(0);
        assertThat(item.get("state").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void aSessionWithoutAResponsibleGroomHasNoGroomName() throws Exception {
        LocalDate day = today().plusDays(1);
        long id = session(eligibleHorse(), day, "08:00", 60, newGroom(List.of()));
        jdbc.update("update training_session set responsible_person_id = null where id = ?", id);
        JsonNode item = day(timetable(trainer, day, day), day).get("items").get(0);
        assertThat(item.get("groomName").isNull()).isTrue();
    }

    @Test
    void theRangeIsChecked() throws Exception {
        LocalDate d = today();
        as(trainer, get("/api/my-timetable").param("from", d.toString()).param("to", d.minusDays(1).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.to").exists());
        as(trainer, get("/api/my-timetable").param("from", d.toString()).param("to", d.plusDays(31).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.to").exists());
        as(trainer, get("/api/my-timetable").param("from", d.toString()).param("to", d.plusDays(30).toString()))
                .andExpect(status().isOk());
    }

    @Test
    void AC_405_onlyAGroomAndAHeadTrainerReadTheirTimetable() throws Exception {
        LocalDate d = today();
        for (Role role : new Role[] {Role.VETERINARIAN, Role.HORSE_OWNER, Role.CLUB_MANAGER}) {
            as(account(role), get("/api/my-timetable").param("from", d.toString()).param("to", d.toString()))
                    .andExpect(status().isForbidden());
        }
        // No parameter names another person: an unknown one is ignored and the caller's own timetable comes back.
        long groom = newGroom(List.of());
        as(groomPrincipal(groom), get("/api/my-timetable").param("from", d.toString()).param("to", d.toString())
                .param("accountId", String.valueOf(trainer.id()))).andExpect(status().isOk());
    }
}
