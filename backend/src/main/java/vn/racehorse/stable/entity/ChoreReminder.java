package vn.racehorse.stable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** The evening reminder evaluated for one club-local day; its existence is what makes it once per day (BR-128). */
@Entity
@Table(name = "chore_reminder")
public class ChoreReminder {

    @Id
    @Column(name = "reminder_date")
    private LocalDate reminderDate;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "notified_grooms", nullable = false)
    private int notifiedGrooms;

    @Column(name = "pending_horses", nullable = false)
    private int pendingHorses;

    @Column(name = "pending_chores", nullable = false)
    private int pendingChores;

    protected ChoreReminder() {
    }

    public ChoreReminder(LocalDate reminderDate, Instant evaluatedAt, int notifiedGrooms, int pendingHorses,
                  int pendingChores) {
        this.reminderDate = reminderDate;
        this.evaluatedAt = evaluatedAt;
        this.notifiedGrooms = notifiedGrooms;
        this.pendingHorses = pendingHorses;
        this.pendingChores = pendingChores;
    }

    public LocalDate getReminderDate() { return reminderDate; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public int getNotifiedGrooms() { return notifiedGrooms; }
    public int getPendingHorses() { return pendingHorses; }
    public int getPendingChores() { return pendingChores; }
}
