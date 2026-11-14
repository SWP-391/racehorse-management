package vn.racehorse.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.payment.entity.InvoicePayment;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {
}
