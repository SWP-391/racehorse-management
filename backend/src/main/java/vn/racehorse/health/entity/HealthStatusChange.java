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

/** One entry of a horse's status history (BR-011). */
@Entity
@Table(name = "health_status_change")
public class HealthStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private HealthStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private HealthStatus toStatus;

    @Column(nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "changed_by_id")
    private UserAccount changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected HealthStatusChange() {
    }

    public HealthStatusChange(Long horseId, HealthStatus fromStatus, HealthStatus toStatus, String reason,
                       UserAccount changedBy, Instant changedAt) {
        this.horseId = horseId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public HealthStatus getFromStatus() {
        return fromStatus;
    }

    public HealthStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public UserAccount getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
