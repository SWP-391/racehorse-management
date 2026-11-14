package vn.racehorse.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.payment.entity.InvoicePaymentCode;

import java.util.Optional;

public interface InvoicePaymentCodeRepository extends JpaRepository<InvoicePaymentCode, Long> {

    Optional<InvoicePaymentCode> findByCode(String code);
}
