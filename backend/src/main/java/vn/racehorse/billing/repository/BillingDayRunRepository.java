package vn.racehorse.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.racehorse.billing.entity.BillingDayRun;

import java.time.LocalDate;
import java.util.Optional;

public interface BillingDayRunRepository extends JpaRepository<BillingDayRun, LocalDate> {

    @Query("select max(r.day) from BillingDayRun r")
    Optional<LocalDate> lastDay();
}
