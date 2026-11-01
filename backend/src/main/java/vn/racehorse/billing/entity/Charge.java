package vn.racehorse.billing.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One amount owed by an owner for one thing done for one horse (BR-179). Never deleted; a cancelled one is voided. */
@Entity
@Table(name = "charge")
public class Charge {

    public enum Category { MEDICINE, FEED, DAILY_FEE, EXAM, ADJUSTMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id")
    private Long horseId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "charge_date", nullable = false)
    private LocalDate chargeDate;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "source_ref")
    private String sourceRef;

    private String reason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Charge() {
    }

    public Long getId() {
        return id;
    }

    public Long getHorseId() {
        return horseId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Category getCategory() {
        return category;
    }

    public LocalDate getChargeDate() {
        return chargeDate;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getReason() {
        return reason;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void voidIt(Instant at, String why) {
        this.voidedAt = at;
        this.voidReason = why;
    }

    public void attach(Long invoiceId) {
        this.invoiceId = invoiceId;
    }
}
