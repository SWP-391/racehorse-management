package vn.racehorse.payment.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.billing.entity.Invoice;

import java.util.Optional;

/** Reads an invoice under a row lock so two notices for one invoice take turns (TECH-233). The invoice is never written. */
public interface InvoiceLockRepository extends JpaRepository<Invoice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);
}
