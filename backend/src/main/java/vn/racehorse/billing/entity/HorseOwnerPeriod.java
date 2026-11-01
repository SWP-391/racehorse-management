package vn.racehorse.billing.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Who owned a horse from one day to another; the open period has no end date (BR-181). */
@Entity
@Table(name = "horse_owner_period")
public class HorseOwnerPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    protected HorseOwnerPeriod() {
    }

    public HorseOwnerPeriod(Long horseId, Long ownerId, LocalDate fromDate) {
        this.horseId = horseId;
        this.ownerId = ownerId;
        this.fromDate = fromDate;
    }

    public Long getId() {
        return id;
    }

    public Long getHorseId() {
        return horseId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public void closeOn(LocalDate toDate) {
        this.toDate = toDate;
    }
}
