package vn.racehorse.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.billing.entity.ServicePrice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ServicePriceRepository extends JpaRepository<ServicePrice, Long> {

    /** The daily fee in force on a day: the latest row not after it; none means no price (BR-178). */
    @Query("""
            select p from ServicePrice p where p.kind = 'DAILY_FEE' and p.effectiveFrom <= :day
            order by p.effectiveFrom desc, p.id desc limit 1""")
    Optional<ServicePrice> dailyOn(@Param("day") LocalDate day);

    @Query("""
            select p from ServicePrice p where p.kind = 'EXAM_FEE' and p.feeTypeId = :feeTypeId
              and p.effectiveFrom <= :day order by p.effectiveFrom desc, p.id desc limit 1""")
    Optional<ServicePrice> examOn(@Param("feeTypeId") Long feeTypeId, @Param("day") LocalDate day);

    @Query("select p from ServicePrice p where p.kind = 'DAILY_FEE' order by p.effectiveFrom desc, p.id desc")
    List<ServicePrice> dailyHistory();

    @Query("select p from ServicePrice p where p.kind = 'EXAM_FEE' and p.feeTypeId = :feeTypeId "
            + "order by p.effectiveFrom desc, p.id desc")
    List<ServicePrice> examHistory(@Param("feeTypeId") Long feeTypeId);

    @Query("select min(p.effectiveFrom) from ServicePrice p where p.kind = 'DAILY_FEE'")
    Optional<LocalDate> firstDailyFeeDate();

    boolean existsByKindAndEffectiveFrom(ServicePrice.Kind kind, LocalDate from);

    boolean existsByKindAndFeeTypeIdAndEffectiveFrom(ServicePrice.Kind kind, Long feeTypeId, LocalDate from);
}
