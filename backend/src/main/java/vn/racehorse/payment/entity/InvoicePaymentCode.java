package vn.racehorse.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The one payment code of an invoice, made the first time its owner opens the payment (BR-219). */
@Entity
@Table(name = "invoice_payment_code")
public class InvoicePaymentCode {

    @Id
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvoicePaymentCode() {
    }

    public InvoicePaymentCode(Long invoiceId, String code, Instant createdAt) {
        this.invoiceId = invoiceId;
        this.code = code;
        this.createdAt = createdAt;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public String getCode() {
        return code;
    }
}
