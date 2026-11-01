package vn.racehorse.billing.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.racehorse.billing.dto.ChargeSpec;
import vn.racehorse.billing.entity.BillingDayRun;
import vn.racehorse.billing.entity.Charge;
import vn.racehorse.billing.entity.ServicePrice;
import vn.racehorse.billing.repository.BillingDayRunRepository;
import vn.racehorse.billing.repository.ServicePriceRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Creates the daily stable and care fee of each club day once the day is over, with catch-up after a gap
 * (BR-178, BR-179, TECH-167). Nothing is charged before the first daily price, which is never dated in the past, so
 * existing horses are not charged for history when billing goes live.
 */
@Service
public class DailyFeeService {

    private final ServicePriceRepository prices;
    private final BillingDayRunRepository runs;
    private final HorseRepository horses;
    private final ChargeService charges;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ClubClock clock;

    public DailyFeeService(ServicePriceRepository prices, BillingDayRunRepository runs, HorseRepository horses,
                           ChargeService charges, JdbcTemplate jdbc, PlatformTransactionManager txm, ClubClock clock) {
        this.prices = prices;
        this.runs = runs;
        this.horses = horses;
        this.charges = charges;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.clock = clock;
    }

    /** Processes every club day up to yesterday that has no run yet; returns how many days were processed. */
    public int run() {
        LocalDate yesterday = clock.today().minusDays(1);
        LocalDate start = runs.lastDay().map(d -> d.plusDays(1)).orElseGet(() -> prices.firstDailyFeeDate().orElse(null));
        int days = 0;
        if (start == null) {
            return 0;
        }
        for (LocalDate day = start; !day.isAfter(yesterday); day = day.plusDays(1)) {
            LocalDate d = day;
            Integer done = tx.execute(status -> processDay(d));
            days += done == null ? 0 : done;
        }
        return days;
    }

    private int processDay(LocalDate day) {
        // One processor at a time, also across instances; the day marker makes a repeat a no-op.
        jdbc.execute("SELECT pg_advisory_xact_lock(7011)");
        if (runs.existsById(day)) {
            return 0;
        }
        int charged = 0;
        BigDecimal price = prices.dailyOn(day).map(ServicePrice::getAmount).orElse(null);
        if (price != null) {
            for (Horse horse : horses.findAll()) {
                if (managedOn(horse, day) && charges.record(new ChargeSpec(horse.getId(), null,
                        Charge.Category.DAILY_FEE, day, "Phí chuồng và công chăm sóc", BigDecimal.ONE, price, null,
                        "FEE_DAY:" + horse.getId() + ":" + day, "FEE_DAY:" + day, null, null))) {
                    charged++;
                }
            }
        }
        runs.save(new BillingDayRun(day, clock.now(), charged));
        return 1;
    }

    /** Registered on or before the day and not archived at its end (BR-178). */
    private boolean managedOn(Horse horse, LocalDate day) {
        LocalDate registered = clock.toClubTime(horse.getCreatedAt()).toLocalDate();
        if (registered.isAfter(day)) {
            return false;
        }
        return horse.getArchivedAt() == null || clock.toClubTime(horse.getArchivedAt()).toLocalDate().isAfter(day);
    }
}
