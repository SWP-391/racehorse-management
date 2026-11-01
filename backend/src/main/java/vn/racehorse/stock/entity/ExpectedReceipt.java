package vn.racehorse.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** A delivery the Club Manager expects; it counts in the days of cover until it is received or cancelled (BR-177). */
@Entity
@Table(name = "stock_expected_receipt")
public class ExpectedReceipt {

    public enum State { EXPECTED, RECEIVED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supply_item_id", nullable = false)
    private Long supplyItemId;

    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    @Column(nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.EXPECTED;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected ExpectedReceipt() {
    }

    public ExpectedReceipt(Long supplyItemId, LocalDate expectedDate, long quantity, Long createdBy, Instant createdAt) {
        this.supplyItemId = supplyItemId;
        this.expectedDate = expectedDate;
        this.quantity = quantity;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void close(State to, Instant at) {
        this.state = to;
        this.closedAt = at;
    }

    public Long getId() { return id; }
    public Long getSupplyItemId() { return supplyItemId; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public long getQuantity() { return quantity; }
    public State getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
}
