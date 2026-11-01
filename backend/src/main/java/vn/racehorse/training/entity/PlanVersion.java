package vn.racehorse.training.entity;

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

/** A snapshot of a plan's schedule, created by post-lock shifts and restores (BR-023, TECH-027). */
@Entity
@Table(name = "plan_version")
public class PlanVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private String snapshot;

    @Column(nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_id")
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlanVersion() {
    }

    public PlanVersion(Long planId, int versionNo, String snapshot, String reason, UserAccount createdBy, Instant createdAt) {
        this.planId = planId;
        this.versionNo = versionNo;
        this.snapshot = snapshot;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public int getVersionNo() { return versionNo; }
    public String getSnapshot() { return snapshot; }
    public String getReason() { return reason; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
