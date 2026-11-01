package vn.racehorse.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.billing.entity.Invoice;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByMonthOrderByNumberAsc(String month);

    Optional<Invoice> findByOwnerIdAndMonth(Long ownerId, String month);

    List<Invoice> findByOwnerIdOrderByMonthDesc(Long ownerId);
}
