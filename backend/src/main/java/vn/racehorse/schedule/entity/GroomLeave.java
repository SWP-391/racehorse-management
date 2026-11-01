package vn.racehorse.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Leave of a Groom on one day; cancelling keeps the record (BR-167, P-02). */
@Entity
@Table(name = "groom_leave")
public class GroomLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    @Column(name = "notice_days", nullable = false)
    private int noticeDays;

    @Column(nullable = false)
    private boolean late;

    private String reason;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private UserAccount recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cancelled_by")
    private UserAccount cancelledBy;

    protected GroomLeave() {
    }

    public GroomLeave(Long accountId, LocalDate leaveDate, int noticeDays, boolean late, String reason, UserAccount recordedBy,
               Instant recordedAt) {
        this.accountId = accountId;
        this.leaveDate = leaveDate;
        this.noticeDays = noticeDays;
        this.late = late;
        this.reason = reason;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public void cancel(UserAccount by, Instant at) {
        this.cancelledBy = by;
        this.cancelledAt = at;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public LocalDate getLeaveDate() {
        return leaveDate;
    }

    public int getNoticeDays() {
        return noticeDays;
    }

    public boolean isLate() {
        return late;
    }

    public String getReason() {
        return reason;
    }

    public UserAccount getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
