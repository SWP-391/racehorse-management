package vn.racehorse.billing.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** A month the Club Manager has closed; its invoices are final (BR-180). */
@Entity
@Table(name = "billing_month")
public class BillingMonth {

    @Id
    @Column(length = 7)
    private String month;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    protected BillingMonth() {
    }

    public String getMonth() {
        return month;
    }
}
