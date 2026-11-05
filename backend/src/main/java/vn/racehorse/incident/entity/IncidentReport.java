package vn.racehorse.incident.entity;

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
import vn.racehorse.region.entity.BodyRegion;

import java.time.Instant;

@Entity
@Table(name = "incident_report")
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "groom_id", nullable = false)
    private UserAccount groom;

    @Column(nullable = false)
    private String description;

    /** Optional region from the one catalog (BR-111). */
    @Enumerated(EnumType.STRING)
    @Column(name = "body_region")
    private BodyRegion bodyRegion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentState state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "acknowledged_by_id")
    private UserAccount acknowledgedBy;

    @Column(name = "closing_note")
    private String closingNote;

    @Column(name = "linked_examination_id")
    private Long linkedExaminationId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "closed_by_id")
    private UserAccount closedBy;

    protected IncidentReport() {
    }

    public IncidentReport(Long horseId, UserAccount groom, String description, BodyRegion bodyRegion, Instant now) {
        this.horseId = horseId;
        this.groom = groom;
        this.description = description;
        this.bodyRegion = bodyRegion;
        this.state = IncidentState.NEW;
        this.createdAt = now;
    }

    public void acknowledge(UserAccount by, Instant at) {
        this.state = IncidentState.ACKNOWLEDGED;
        this.acknowledgedBy = by;
        this.acknowledgedAt = at;
    }

    public void close(UserAccount by, String note, Long examinationId, Instant at) {
        this.state = IncidentState.HANDLED;
        this.closedBy = by;
        this.closingNote = note;
        this.linkedExaminationId = examinationId;
        this.closedAt = at;
    }

    public Long getId() { return id; }
    public Long getHorseId() { return horseId; }
    public UserAccount getGroom() { return groom; }
    public String getDescription() { return description; }
    public BodyRegion getBodyRegion() { return bodyRegion; }
    public IncidentState getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public String getClosingNote() { return closingNote; }
    public Long getLinkedExaminationId() { return linkedExaminationId; }
    public Instant getClosedAt() { return closedAt; }
}
