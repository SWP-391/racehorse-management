package vn.racehorse.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One line of the stock ledger; never changed afterwards, a mistake is a reversal line (BR-171, TECH-152). */
@Entity
@Table(name = "stock_movement")
public class StockMovement {

    public enum Kind { RECEIPT, USE_CARE, USE_FEED, REVERSAL, ADJUSTMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supply_item_id", nullable = false)
    private Long supplyItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false)
    private long delta;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "reverses_id")
    private Long reversesId;

    private String reason;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(Long supplyItemId, Kind kind, long delta, long balanceAfter, BigDecimal unitPrice, LocalDate occurredOn,
                  String refType, Long refId, Long reversesId, String reason, Long actorId, Instant createdAt) {
        this.supplyItemId = supplyItemId;
        this.kind = kind;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.unitPrice = unitPrice;
        this.occurredOn = occurredOn;
        this.refType = refType;
        this.refId = refId;
        this.reversesId = reversesId;
        this.reason = reason;
        this.actorId = actorId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getSupplyItemId() { return supplyItemId; }
    public Kind getKind() { return kind; }
    public long getDelta() { return delta; }
    public long getBalanceAfter() { return balanceAfter; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public String getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public Long getReversesId() { return reversesId; }
    public String getReason() { return reason; }
    public Long getActorId() { return actorId; }
    public Instant getCreatedAt() { return createdAt; }
}
