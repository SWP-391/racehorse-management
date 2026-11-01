package vn.racehorse.billing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.billing.entity.HorseOwnerPeriod;
import vn.racehorse.billing.repository.HorseOwnerPeriodRepository;

import java.time.LocalDate;
import java.util.List;

/** Keeps who owned a horse on which day, so a charge goes to the owner of its day (BR-181, TECH-169). */
@Service
public class OwnerPeriodService {

    private final HorseOwnerPeriodRepository periods;

    public OwnerPeriodService(HorseOwnerPeriodRepository periods) {
        this.periods = periods;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void open(Long horseId, Long ownerId, LocalDate from) {
        periods.save(new HorseOwnerPeriod(horseId, ownerId, from));
    }

    /**
     * The owner of the horse changes today: the open period ends yesterday and a new one starts today. A period
     * that opened today (a second change on the same day) is simply corrected, so no empty period remains.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void change(Long horseId, Long newOwnerId, LocalDate today) {
        HorseOwnerPeriod open = periods.findByHorseIdAndToDateIsNull(horseId).orElse(null);
        if (open == null) {
            periods.save(new HorseOwnerPeriod(horseId, newOwnerId, today));
            return;
        }
        if (open.getOwnerId().equals(newOwnerId)) {
            return;
        }
        if (!open.getFromDate().isBefore(today)) {
            open.setOwnerId(newOwnerId);
            return;
        }
        open.closeOn(today.minusDays(1));
        periods.flush();
        periods.save(new HorseOwnerPeriod(horseId, newOwnerId, today));
    }

    /** The owner on a day; before the first period the first owner, and the open period for any later day. */
    @Transactional(readOnly = true)
    public Long ownerOn(Long horseId, LocalDate date, Long fallbackOwnerId) {
        List<HorseOwnerPeriod> all = periods.findByHorseIdOrderByFromDateAsc(horseId);
        for (HorseOwnerPeriod p : all) {
            if (!date.isBefore(p.getFromDate()) && (p.getToDate() == null || !date.isAfter(p.getToDate()))) {
                return p.getOwnerId();
            }
        }
        return all.isEmpty() ? fallbackOwnerId : all.get(0).getOwnerId();
    }
}
