package vn.racehorse.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.billing.entity.InvoiceLine;

import java.util.List;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    List<InvoiceLine> findByInvoiceIdOrderByIdAsc(Long invoiceId);
}
