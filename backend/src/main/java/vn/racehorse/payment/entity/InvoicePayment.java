package vn.racehorse.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An invoice is paid exactly when this row exists; the invoice itself is never changed (BR-224). */
@Entity
@Table(name = "invoice_payment")
public class InvoicePayment {

    public static final String TRANSFER = "TRANSFER";
    public static final String ASSIGNED = "ASSIGNED";

    @Id
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(nullable = false)
    private String via;

    protected InvoicePayment() {
    }

    public InvoicePayment(Long invoiceId, Long transferId, Instant paidAt, String via) {
        this.invoiceId = invoiceId;
        this.transferId = transferId;
        this.paidAt = paidAt;
        this.via = via;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public Long getTransferId() {
        return transferId;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getVia() {
        return via;
    }
}
