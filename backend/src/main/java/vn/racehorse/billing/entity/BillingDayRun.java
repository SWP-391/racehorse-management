package vn.racehorse.billing.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Marks a club day whose daily fee has been created (BR-179). */
@Entity
@Table(name = "billing_day_run")
public class BillingDayRun {

    @Id
    private LocalDate day;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "horses_charged", nullable = false)
    private int horsesCharged;

    protected BillingDayRun() {
    }

    public BillingDayRun(LocalDate day, Instant processedAt, int horsesCharged) {
        this.day = day;
        this.processedAt = processedAt;
        this.horsesCharged = horsesCharged;
    }

    public LocalDate getDay() {
        return day;
    }
}
