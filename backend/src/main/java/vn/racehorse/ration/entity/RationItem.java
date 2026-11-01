package vn.racehorse.ration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.racehorse.stable.entity.SupplyItem;

import java.math.BigDecimal;

/** One item of a meal, immutable once saved (BR-139, BR-140). */
@Entity
@Table(name = "ration_item")
public class RationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id")
    private RationMeal meal;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType type;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "supply_item_id")
    private SupplyItem supplyItem;

    @Column(nullable = false)
    private BigDecimal quantity;

    protected RationItem() {
    }

    public RationItem(RationMeal meal, int position, ItemType type, SupplyItem supplyItem, BigDecimal quantity) {
        this.meal = meal;
        this.position = position;
        this.type = type;
        this.supplyItem = supplyItem;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public int getPosition() { return position; }
    public ItemType getType() { return type; }
    public SupplyItem getSupplyItem() { return supplyItem; }
    public BigDecimal getQuantity() { return quantity; }
}
