package vn.racehorse.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One price change of a supply item, kept as history (BR-171, P-03). */
@Entity
@Table(name = "supply_price")
public class SupplyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supply_item_id", nullable = false)
    private Long supplyItemId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "set_by")
    private Long setBy;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    protected SupplyPrice() {
    }

    public SupplyPrice(Long supplyItemId, BigDecimal price, LocalDate effectiveFrom, Long setBy, Instant setAt) {
        this.supplyItemId = supplyItemId;
        this.price = price;
        this.effectiveFrom = effectiveFrom;
        this.setBy = setBy;
        this.setAt = setAt;
    }

    public Long getId() { return id; }
    public Long getSupplyItemId() { return supplyItemId; }
    public BigDecimal getPrice() { return price; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public Long getSetBy() { return setBy; }
    public Instant getSetAt() { return setAt; }
}
