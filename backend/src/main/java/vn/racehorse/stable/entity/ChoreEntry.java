package vn.racehorse.stable.entity;

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

/** One chore marked done for one horse and day; cancelling keeps the row as history (BR-125, BR-126). */
@Entity
@Table(name = "chore_entry")
public class ChoreEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "chore_date", nullable = false)
    private LocalDate choreDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Chore chore;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "groom_id", nullable = false)
    private UserAccount groom;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cancelled_by")
    private UserAccount cancelledBy;

    /** Marked outside the Groom's barn hours or on leave: shown to staff as "làm thay" (BR-169). */
    @Column(name = "on_behalf", nullable = false)
    private boolean onBehalf;

    /** Filled in by the Club Manager for a past day, with the reason (BR-174); the {@code groom} is then the Club Manager. */
    @Column(name = "backfilled", nullable = false)
    private boolean backfilled;

    @Column(name = "backfill_reason")
    private String backfillReason;

    protected ChoreEntry() {
    }

    public ChoreEntry(Long horseId, LocalDate choreDate, Chore chore, UserAccount groom, Instant markedAt, boolean onBehalf) {
        this.onBehalf = onBehalf;
        this.horseId = horseId;
        this.choreDate = choreDate;
        this.chore = chore;
        this.groom = groom;
        this.markedAt = markedAt;
    }

    public static ChoreEntry backfill(Long horseId, LocalDate choreDate, Chore chore, UserAccount manager, Instant markedAt,
                               String reason) {
        ChoreEntry e = new ChoreEntry(horseId, choreDate, chore, manager, markedAt, false);
        e.backfilled = true;
        e.backfillReason = reason;
        return e;
    }

    public void cancel(UserAccount by, String reason, Instant at) {
        this.cancelledBy = by;
        this.cancelReason = reason;
        this.cancelledAt = at;
    }

    public boolean isCancelled() { return cancelledAt != null; }
    public boolean isOnBehalf() { return onBehalf; }
    public boolean isBackfilled() { return backfilled; }
    public String getBackfillReason() { return backfillReason; }
    public Long getId() { return id; }
    public Long getHorseId() { return horseId; }
    public LocalDate getChoreDate() { return choreDate; }
    public Chore getChore() { return chore; }
    public UserAccount getGroom() { return groom; }
    public Instant getMarkedAt() { return markedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public UserAccount getCancelledBy() { return cancelledBy; }
}
