package vn.racehorse.stable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** A catalog item; deactivated, never deleted (BR-130). */
@Entity
@Table(name = "supply_item")
public class SupplyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplyCategory category;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private SupplyUnit unitRef;

    /** The balance of the stock ledger; only {@code StockService} changes it (BR-171). */
    @Column(nullable = false)
    private long quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SupplyItem() {
    }

    public SupplyItem(String name, SupplyCategory category, SupplyUnit unit, BigDecimal unitPrice, Instant createdAt) {
        this.name = name;
        this.category = category;
        this.unitRef = unit;
        this.unitPrice = unitPrice;
        this.createdAt = createdAt;
    }

    public void update(String name, SupplyCategory category, SupplyUnit unit, boolean active) {
        this.name = name;
        this.category = category;
        this.unitRef = unit;
        this.active = active;
    }

    /** Applies a ledger line to the balance; the caller holds the row lock and has checked the result (BR-171). */
    public void applyDelta(long delta) {
        this.quantity += delta;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public SupplyCategory getCategory() { return category; }
    public String getUnit() { return unitRef.getName(); }
    public Long getUnitId() { return unitRef.getId(); }
    public long getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
