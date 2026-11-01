package vn.racehorse.stable.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.schedule.service.GroomScheduleService;
import vn.racehorse.stable.dto.StableCareReminderResult;
import vn.racehorse.stable.entity.Chore;
import vn.racehorse.stable.entity.ChoreEntry;
import vn.racehorse.stable.entity.ChoreReminder;
import vn.racehorse.stable.repository.ChoreReminderRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/** The evening reminder for unmarked chores: 18:00 club time, once per day, to each Groom on duty that day (BR-128). */
@Service
public class StableCareService {

    /** Fixed, not a setting (DEC-052). */
    static final LocalTime REMINDER_TIME = LocalTime.of(18, 0);

    private final HorseRepository horses;
    private final ChoreService chores;
    private final ChoreReminderRepository reminders;
    private final UserAccountRepository accounts;
    private final NotificationService notifications;
    private final ClubClock clock;
    private final GroomScheduleService schedules;

    public StableCareService(HorseRepository horses, ChoreService chores, ChoreReminderRepository reminders,
                             UserAccountRepository accounts, NotificationService notifications, ClubClock clock,
                             GroomScheduleService schedules) {
        this.schedules = schedules;
        this.horses = horses;
        this.chores = chores;
        this.reminders = reminders;
        this.accounts = accounts;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public StableCareReminderResult sendEveningReminder() {
        ZonedDateTime now = clock.toClubTime(clock.now());
        LocalDate day = now.toLocalDate();
        if (now.toLocalTime().isBefore(REMINDER_TIME) || reminders.existsById(day)) {
            return StableCareReminderResult.NOT_EVALUATED;
        }
        Map<Long, Map<Chore, ChoreEntry>> done = chores.doneByHorse(day);
        int pendingHorses = 0;
        int pendingChores = 0;
        for (Horse h : horses.findAllByOrderByNameAsc()) {
            if (h.isArchived()) {
                continue;
            }
            int pending = Chore.values().length - done.getOrDefault(h.getId(), Map.of()).size();
            if (pending > 0) {
                pendingHorses++;
                pendingChores += pending;
            }
        }
        // Only Grooms on duty that day are reminded: hours that weekday or no schedule at all, and no leave (BR-128).
        List<UserAccount> onDuty = pendingChores == 0 ? List.of() : accounts.findByRoleOrderByDisplayNameAsc(Role.GROOM)
                .stream().filter(UserAccount::isActive).filter(a -> schedules.onDuty(a.getId(), day)).toList();
        // The row is written even when nothing is pending or nobody is on duty, so the day is evaluated once (AC-235).
        reminders.saveAndFlush(new ChoreReminder(day, clock.now(), onDuty.size(), pendingHorses, pendingChores));
        String text = "Nhắc việc 18:00: còn " + pendingHorses + " ngựa chưa hoàn thành " + pendingChores
                + " việc chăm sóc hôm nay.";
        onDuty.forEach(g -> notifications.notifyAccount(g.getId(), text, "/chores"));
        return new StableCareReminderResult(true, pendingHorses, pendingChores, onDuty.size());
    }
}
