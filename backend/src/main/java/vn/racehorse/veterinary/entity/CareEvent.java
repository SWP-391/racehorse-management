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

/** History of a care item: completions, overrides and postponements (BR-062, BR-063). Append-only. */
@Entity
@Table(name = "care_event")
public class CareEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareEventKind kind;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    private String performer;
    private String product;
    private String batch;
    private String note;

    /** The medicine taken from stock and the price of that moment; empty on old events and hoof checks (BR-172). */
    @Column(name = "supply_item_id")
    private Long supplyItemId;

    private Long quantity;

    @Column(name = "unit_price")
    private java.math.BigDecimal unitPrice;

    @Column(name = "previous_due", nullable = false)
    private LocalDate previousDue;

    @Column(name = "new_due", nullable = false)
    private LocalDate newDue;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "by_id", nullable = false)
    private UserAccount by;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected CareEvent() {
    }

    public CareEvent(Long itemId, CareEventKind kind, LocalDate eventDate, String performer, String product, String batch,
              String note, LocalDate previousDue, LocalDate newDue, UserAccount by, Instant at) {
        this.itemId = itemId;
        this.kind = kind;
        this.eventDate = eventDate;
        this.performer = performer;
        this.product = product;
        this.batch = batch;
        this.note = note;
        this.previousDue = previousDue;
        this.newDue = newDue;
        this.by = by;
        this.at = at;
    }

    public void takeFromStock(Long supplyItemId, Long quantity, java.math.BigDecimal unitPrice) {
        this.supplyItemId = supplyItemId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() { return id; }
    public Long getSupplyItemId() { return supplyItemId; }
    public Long getQuantity() { return quantity; }
    public java.math.BigDecimal getUnitPrice() { return unitPrice; }
    public Long getItemId() { return itemId; }
    public CareEventKind getKind() { return kind; }
    public LocalDate getEventDate() { return eventDate; }
    public String getPerformer() { return performer; }
    public String getProduct() { return product; }
    public String getBatch() { return batch; }
    public String getNote() { return note; }
    public LocalDate getPreviousDue() { return previousDue; }
    public LocalDate getNewDue() { return newDue; }
    public UserAccount getBy() { return by; }
    public Instant getAt() { return at; }
}
