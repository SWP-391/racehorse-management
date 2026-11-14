package vn.racehorse.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** A transfer in that the bank-transfer service announced, applied to an invoice or kept unmatched (BR-220, BR-221). */
@Entity
@Table(name = "bank_transfer")
public class BankTransfer {

    public static final String APPLIED = "APPLIED";
    public static final String UNMATCHED = "UNMATCHED";
    public static final String ASSIGNED = "ASSIGNED";

    public static final String NO_CODE = "NO_CODE";
    public static final String UNKNOWN_CODE = "UNKNOWN_CODE";
    public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String ALREADY_PAID = "ALREADY_PAID";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String content;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String reference;

    @Column(name = "transferred_at")
    private Instant transferredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private boolean simulated;

    @Column(nullable = false)
    private String status;

    private String reason;

    @Column(name = "invoice_id")
    private Long invoiceId;

    protected BankTransfer() {
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getContent() {
        return content;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getReference() {
        return reference;
    }

    public Instant getTransferredAt() {
        return transferredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void keepUnmatched(String reason) {
        this.status = UNMATCHED;
        this.reason = reason;
    }

    public void apply(Long invoiceId, String status) {
        this.status = status;
        this.invoiceId = invoiceId;
    }
}
