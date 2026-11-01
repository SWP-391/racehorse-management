package vn.racehorse.health.entity;

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

/** A lock requested while the horse is training; applied when the session ends (BR-020). */
@Entity
@Table(name = "pending_lock_request")
public class PendingLockRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private UserAccount requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "applied_lock_id")
    private Long appliedLockId;

    protected PendingLockRequest() {
    }

    public PendingLockRequest(Long horseId, Long sessionId, String reason, UserAccount requestedBy, Instant requestedAt) {
        this.horseId = horseId;
        this.sessionId = sessionId;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    public void withdraw(Instant at) {
        withdrawnAt = at;
    }

    public void applied(Long lockId, Instant at) {
        appliedLockId = lockId;
        appliedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getHorseId() {
        return horseId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public String getReason() {
        return reason;
    }

    public UserAccount getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
