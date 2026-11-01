package vn.racehorse.billing.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** The immutable invoice of one owner for one closed month (BR-180). */
@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 7)
    private String month;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    protected Invoice() {
    }

    public Invoice(Long ownerId, String month, String number, BigDecimal total, Long closedBy, Instant closedAt) {
        this.ownerId = ownerId;
        this.month = month;
        this.number = number;
        this.total = total;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getMonth() {
        return month;
    }

    public String getNumber() {
        return number;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
