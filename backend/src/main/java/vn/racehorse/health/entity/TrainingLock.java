package vn.racehorse.health.entity;

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

/** A training lock (BR-015 to BR-019). Active while not lifted; never expires and is never deleted. */
@Entity
@Table(name = "training_lock")
public class TrainingLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LockKind kind;

    @Column(nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "placed_by_id")
    private UserAccount placedBy;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lifted_by_id")
    private UserAccount liftedBy;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_level")
    private ReturnLevel returnLevel;

    protected TrainingLock() {
    }

    public TrainingLock(Long horseId, LockKind kind, String reason, UserAccount placedBy, Instant placedAt,
                 LocalDate reviewDate) {
        this.horseId = horseId;
        this.kind = kind;
        this.reason = reason;
        this.placedBy = placedBy;
        this.placedAt = placedAt;
        this.reviewDate = reviewDate;
    }

    public void lift(UserAccount by, Instant at, ReturnLevel level) {
        this.liftedBy = by;
        this.liftedAt = at;
        this.returnLevel = level;
    }

    public boolean isActive() {
        return liftedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Long getHorseId() {
        return horseId;
    }

    public LockKind getKind() {
        return kind;
    }

    public String getReason() {
        return reason;
    }

    public UserAccount getPlacedBy() {
        return placedBy;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public Instant getLiftedAt() {
        return liftedAt;
    }

    public ReturnLevel getReturnLevel() {
        return returnLevel;
    }
}
