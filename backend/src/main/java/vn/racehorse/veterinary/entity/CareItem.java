package vn.racehorse.veterinary.entity;

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
import vn.racehorse.account.entity.UserAccount;

import java.time.Instant;
import java.time.LocalDate;

/** A recurring care item of one horse; the Veterinarian sets its interval, nothing is built in (BR-061). */
@Entity
@Table(name = "care_item")
public class CareItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_type", nullable = false)
    private CareType type;

    @Column(nullable = false)
    private String name;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "next_due", nullable = false)
    private LocalDate nextDue;

    /** The medicine and dose the Veterinarian plans for the next completion; both set or both empty (BR-175). */
    @Column(name = "planned_supply_item_id")
    private Long plannedSupplyItemId;

    @Column(name = "planned_quantity")
    private Long plannedQuantity;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CareItem() {
    }

    public CareItem(Long horseId, CareType type, String name, int intervalDays, LocalDate nextDue, UserAccount createdBy,
             Instant createdAt) {
        this.horseId = horseId;
        this.type = type;
        this.name = name;
        this.intervalDays = intervalDays;
        this.nextDue = nextDue;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void setNextDue(LocalDate nextDue) {
        this.nextDue = nextDue;
    }

    public void setPlan(Long supplyItemId, Long quantity) {
        this.plannedSupplyItemId = supplyItemId;
        this.plannedQuantity = quantity;
    }

    public Long getId() { return id; }
    public Long getHorseId() { return horseId; }
    public CareType getType() { return type; }
    public String getName() { return name; }
    public int getIntervalDays() { return intervalDays; }
    public LocalDate getNextDue() { return nextDue; }
    public Long getPlannedSupplyItemId() { return plannedSupplyItemId; }
    public Long getPlannedQuantity() { return plannedQuantity; }
}
