package vn.racehorse.billing.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One price of the service price list; never edited, a new row with a later date replaces it (BR-178). */
@Entity
@Table(name = "service_price")
public class ServicePrice {

    public enum Kind { DAILY_FEE, EXAM_FEE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "fee_type_id")
    private Long feeTypeId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ServicePrice() {
    }

    public ServicePrice(Kind kind, Long feeTypeId, BigDecimal amount, LocalDate effectiveFrom, Long createdBy, Instant now) {
        this.kind = kind;
        this.feeTypeId = feeTypeId;
        this.amount = amount;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getFeeTypeId() {
        return feeTypeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }
}
