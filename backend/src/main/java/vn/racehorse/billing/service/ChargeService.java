package vn.racehorse.billing.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.billing.dto.ChargeSpec;
import vn.racehorse.billing.entity.Charge;
import vn.racehorse.billing.repository.BillingMonthRepository;
import vn.racehorse.billing.repository.ChargeRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The one place that creates a charge (TECH-165). It resolves the owner of the work day, rounds each line half up to
 * whole dong, never creates the same fact twice and never touches health, locks or sessions (BR-183).
 */
@Service
public class ChargeService {

    private final JdbcTemplate jdbc;
    private final ChargeRepository charges;
    private final BillingMonthRepository months;
    private final OwnerPeriodService owners;
    private final HorseRepository horses;
    private final ClubClock clock;

    public ChargeService(JdbcTemplate jdbc, ChargeRepository charges, BillingMonthRepository months,
                         OwnerPeriodService owners, HorseRepository horses, ClubClock clock) {
        this.jdbc = jdbc;
        this.charges = charges;
        this.months = months;
        this.owners = owners;
        this.horses = horses;
        this.clock = clock;
    }

    /** True when a charge was created, false when the same fact had been charged already. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(ChargeSpec s) {
        Long owner = s.ownerId() != null ? s.ownerId() : ownerOf(s.horseId(), s.workDate());
        BigDecimal amount = (s.amount() != null ? s.amount() : s.quantity().multiply(s.unitPrice()))
                .setScale(0, RoundingMode.HALF_UP).setScale(2);
        int created = jdbc.update("""
                INSERT INTO charge (horse_id, owner_id, category, charge_date, work_date, description, quantity,
                                    unit_price, amount, idempotency_key, source_ref, reason, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING""",
                s.horseId(), owner, s.category().name(), statementDate(s.workDate()), s.workDate(), s.description(),
                s.quantity(), s.unitPrice(), amount, s.key(), s.sourceRef(), s.reason(), s.createdBy(),
                Timestamp.from(clock.now()));
        return created == 1;
    }

    /**
     * A cancelled mark removes its charges; a charge already on an invoice is compensated by a negative line in
     * the first open month instead, because a closed invoice never changes (BR-180).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void voidBySource(String sourceRef, String why) {
        for (Charge c : charges.findBySourceRefAndVoidedAtIsNull(sourceRef)) {
            if (c.getInvoiceId() == null) {
                c.voidIt(clock.now(), why);
            } else {
                record(new ChargeSpec(c.getHorseId(), c.getOwnerId(), Charge.Category.ADJUSTMENT, clock.today(), why
                        + ": " + c.getDescription(), BigDecimal.ONE, c.getAmount().negate(), c.getAmount().negate(),
                        "VOID:" + c.getId(), sourceRef, why, null));
            }
        }
    }

    /** The owner of the horse on a day (BR-181). */
    @Transactional(readOnly = true)
    public Long ownerOf(Long horseId, LocalDate date) {
        Horse horse = horses.findById(horseId).orElseThrow();
        return owners.ownerOn(horseId, date, horse.getOwner().getId());
    }

    /** The statement day of work: the day itself, or the first day of the first open month after a closed one. */
    @Transactional(readOnly = true)
    public LocalDate statementDate(LocalDate workDate) {
        YearMonth month = YearMonth.from(workDate);
        if (!months.existsById(month.toString())) {
            return workDate;
        }
        do {
            month = month.plusMonths(1);
        } while (months.existsById(month.toString()));
        return month.atDay(1);
    }
}
