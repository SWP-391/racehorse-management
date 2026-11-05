package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.racehorse.account.entity.Role;
import vn.racehorse.notification.entity.Notification;
import vn.racehorse.notification.repository.NotificationRepository;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.stable.dto.StableCareReminderResult;
import vn.racehorse.stable.repository.ChoreReminderRepository;
import vn.racehorse.stable.service.StableCareService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The 18:00 reminder (BR-128). Each test works on its own far-future club date so the once-per-day row of one test
 * never meets another; the shared database holds horses from other tests, so everything except the horses under test
 * is first marked done for that date.
 */
class ChoreReminderTest extends StableTestBase {

    private static final String MARKER = "Nhắc việc 18:00";
    private static final List<String> CHORES = List.of("FEEDING", "STALL_CLEANING", "WASHING", "ICE_SOAK");

    @Autowired
    StableCareService stableCare;
    @Autowired
    ChoreReminderRepository reminders;
    @Autowired
    NotificationRepository notificationRows;
    @Autowired
    JdbcTemplate jdbc;

    /** Marks the chores of every non-archived horse for the date, except {@code leaveOpen} (horse id to chores left). */
    private void markEverythingExcept(LocalDate date, java.util.Map<Long, Set<String>> leaveOpen) {
        for (Long horse : jdbc.queryForList("select id from horse where archived_at is null", Long.class)) {
            Set<String> open = leaveOpen.getOrDefault(horse, Set.of());
            for (String chore : CHORES) {
                if (!open.contains(chore)) {
                    jdbc.update("insert into chore_entry (horse_id, chore_date, chore, groom_id, marked_at) values (?, ?, ?, ?, ?)",
                            horse, java.sql.Date.valueOf(date), chore, groom.id(), Timestamp.from(Instant.now()));
                }
            }
        }
    }

    private long reminders(AccountPrincipal who) {
        return notificationRows.findByRecipientIdOrderByCreatedAtDescIdDesc(who.id(), PageRequest.of(0, 100)).stream()
                .map(Notification::getMessage).filter(m -> m.contains(MARKER)).count();
    }

    @Test
    void AC_233_everyActiveGroomGetsOneNotificationNamingHorsesAndChoresPending() throws Exception {
        long a = newHorse();
        long b = newHorse();
        AccountPrincipal otherGroom = account(Role.GROOM);
        LocalDate day = shiftTo(2001, LocalTime.of(18, 0));
        markEverythingExcept(day, java.util.Map.of(a, Set.copyOf(CHORES), b, Set.of("WASHING")));

        StableCareReminderResult result = stableCare.sendEveningReminder();
        assertThat(result.evaluated()).isTrue();
        assertThat(result.pendingHorses()).isEqualTo(2);
        assertThat(result.pendingChores()).isEqualTo(5);
        for (AccountPrincipal who : List.of(groom, otherGroom)) {
            assertThat(reminders(who)).isEqualTo(1);
            assertThat(hasNotification(who, "còn 2 ngựa chưa hoàn thành 5 việc")).isTrue();
        }
        assertThat(reminders.findById(day).orElseThrow().getPendingChores()).isEqualTo(5);
    }

    @Test
    void AC_234_nothingPendingMeansNoNotificationButTheDayIsStillEvaluated() throws Exception {
        newHorse();
        LocalDate day = shiftTo(2003, LocalTime.of(18, 0));
        markEverythingExcept(day, java.util.Map.of());

        StableCareReminderResult result = stableCare.sendEveningReminder();
        assertThat(result.evaluated()).isTrue();
        assertThat(result.pendingChores()).isZero();
        assertThat(reminders(groom)).isZero();
        assertThat(reminders.existsById(day)).isTrue();
    }

    @Test
    void beforeEighteenNothingIsEvaluated() throws Exception {
        long a = newHorse();
        LocalDate day = shiftTo(2005, LocalTime.of(17, 59));
        assertThat(stableCare.sendEveningReminder().evaluated()).isFalse();
        assertThat(reminders.existsById(day)).isFalse();
        assertThat(reminders(groom)).isZero();
        assertThat(a).isPositive();
    }

    @Test
    void AC_235_noSecondNotificationThatDayEvenAfterACancelAndTheNextDayIsEvaluatedAnew() throws Exception {
        long a = newHorse();
        LocalDate day = shiftTo(2007, LocalTime.of(18, 0));
        markEverythingExcept(day, java.util.Map.of(a, Set.of("FEEDING")));
        assertThat(stableCare.sendEveningReminder().evaluated()).isTrue();
        assertThat(reminders(groom)).isEqualTo(1);

        shiftTo(2007, LocalTime.of(21, 30));
        assertThat(stableCare.sendEveningReminder().evaluated()).isFalse();
        // Marking and then cancelling after 18:00 creates no second reminder either.
        long entry = body(mark(groom, a, "FEEDING").andExpect(status().isCreated())).get("entryId").asLong();
        cancel(groom, entry, "nhầm").andExpect(status().isOk());
        assertThat(stableCare.sendEveningReminder().evaluated()).isFalse();
        assertThat(reminders(groom)).isEqualTo(1);

        LocalDate next = shiftTo(2008, LocalTime.of(18, 5));
        markEverythingExcept(next, java.util.Map.of(a, Set.of("WASHING", "ICE_SOAK")));
        StableCareReminderResult second = stableCare.sendEveningReminder();
        assertThat(second.evaluated()).isTrue();
        assertThat(second.pendingHorses()).isEqualTo(1);
        assertThat(second.pendingChores()).isEqualTo(2);
        assertThat(reminders(groom)).isEqualTo(2);
    }

    @Test
    void AC_236_onlyActiveGroomAccountsGetTheReminder() throws Exception {
        long a = newHorse();
        AccountPrincipal retired = account(Role.GROOM);
        as(manager, post("/api/accounts/" + retired.id() + "/deactivate")).andExpect(status().isOk());
        LocalDate day = shiftTo(2010, LocalTime.of(18, 0));
        markEverythingExcept(day, java.util.Map.of(a, Set.copyOf(CHORES)));

        stableCare.sendEveningReminder();
        assertThat(reminders(groom)).isEqualTo(1);
        for (AccountPrincipal who : List.of(trainer, vet, owner, manager, retired)) {
            assertThat(reminders(who)).as(who.role().name()).isZero();
        }
    }

    private void setHours(AccountPrincipal who, List<java.util.Map<String, Object>> ranges) throws Exception {
        asJson(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/accounts/" + who.id() + "/schedule"),
                java.util.Map.of("ranges", ranges)).andExpect(status().isOk());
    }

    @Test
    void AC_233_onlyGroomsOnDutyThatDayAreRemindedNotThoseOnLeaveOrOffThatWeekday() throws Exception {
        long a = newHorse();
        AccountPrincipal onHours = account(Role.GROOM);
        AccountPrincipal offThatDay = account(Role.GROOM);
        AccountPrincipal onLeave = account(Role.GROOM);
        LocalDate day = shiftTo(2013, LocalTime.of(18, 0));
        int weekday = day.getDayOfWeek().getValue();
        setHours(onHours, List.of(java.util.Map.of("weekday", weekday, "start", "05:00", "end", "12:00")));
        setHours(offThatDay, List.of(java.util.Map.of("weekday", weekday % 7 + 1, "start", "05:00", "end", "12:00")));
        setHours(onLeave, List.of(java.util.Map.of("weekday", weekday, "start", "05:00", "end", "12:00")));
        asJson(manager, post("/api/accounts/" + onLeave.id() + "/leave"), java.util.Map.of("date", day.toString()))
                .andExpect(status().isCreated());
        markEverythingExcept(day, java.util.Map.of(a, Set.copyOf(CHORES)));

        StableCareReminderResult result = stableCare.sendEveningReminder();
        assertThat(result.evaluated()).isTrue();
        assertThat(reminders(onHours)).isEqualTo(1);
        assertThat(reminders(groom)).as("a Groom with no schedule counts as on duty").isEqualTo(1);
        assertThat(reminders(offThatDay)).isZero();
        assertThat(reminders(onLeave)).isZero();
        assertThat(reminders.findById(day).orElseThrow().getNotifiedGrooms())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void AC_235_aDayWithNoGroomOnDutyIsStillEvaluatedOnce() throws Exception {
        long a = newHorse();
        LocalDate day = shiftTo(2015, LocalTime.of(18, 0));
        int weekday = day.getDayOfWeek().getValue();
        for (Long id : jdbc.queryForList("select id from user_account where role = 'GROOM' and active", Long.class)) {
            jdbc.update("insert into groom_leave (account_id, leave_date, notice_days, late, recorded_by, recorded_at)"
                            + " values (?, ?, 0, true, ?, now()) on conflict do nothing",
                    id, java.sql.Date.valueOf(day), manager.id());
        }
        markEverythingExcept(day, java.util.Map.of(a, Set.copyOf(CHORES)));
        assertThat(stableCare.sendEveningReminder().evaluated()).isTrue();
        assertThat(reminders.findById(day).orElseThrow().getNotifiedGrooms()).isZero();
        assertThat(stableCare.sendEveningReminder().evaluated()).isFalse();
        assertThat(weekday).isBetween(1, 7);
    }
}
