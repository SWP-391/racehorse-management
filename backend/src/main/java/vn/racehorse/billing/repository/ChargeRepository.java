package vn.racehorse.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.billing.entity.Charge;

import java.time.LocalDate;
import java.util.List;

public interface ChargeRepository extends JpaRepository<Charge, Long> {

    List<Charge> findBySourceRefAndVoidedAtIsNull(String sourceRef);

    /** Charges of an owner for one statement month, cancelled ones left out. */
    @Query("select c from Charge c where c.ownerId = :owner and c.voidedAt is null "
            + "and c.chargeDate >= :from and c.chargeDate <= :to order by c.workDate, c.id")
    List<Charge> forOwnerInMonth(@Param("owner") Long owner, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select c from Charge c where c.voidedAt is null and c.chargeDate >= :from and c.chargeDate <= :to "
            + "order by c.ownerId, c.workDate, c.id")
    List<Charge> allInMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Charges of one horse for a month, every owner (the Club Manager's horse view panel). */
    @Query("select c from Charge c where c.horseId = :horse and c.voidedAt is null "
            + "and c.chargeDate >= :from and c.chargeDate <= :to order by c.workDate, c.id")
    List<Charge> forHorseInMonth(@Param("horse") Long horse, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Charges of one horse and one owner for a month (the owner's horse view panel). */
    @Query("select c from Charge c where c.horseId = :horse and c.ownerId = :owner and c.voidedAt is null "
            + "and c.chargeDate >= :from and c.chargeDate <= :to order by c.workDate, c.id")
    List<Charge> forHorseAndOwnerInMonth(@Param("horse") Long horse, @Param("owner") Long owner,
                                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByHorseIdAndOwnerId(Long horseId, Long ownerId);

    /** Not yet invoiced charges dated before a day, to find a month that is still open. */
    @Query("select min(c.chargeDate) from Charge c where c.voidedAt is null and c.invoiceId is null "
            + "and c.chargeDate < :before")
    java.util.Optional<LocalDate> earliestOpenBefore(@Param("before") LocalDate before);
}
