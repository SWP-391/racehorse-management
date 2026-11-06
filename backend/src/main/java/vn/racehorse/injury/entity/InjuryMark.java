package vn.racehorse.injury.entity;

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

/** One injury of one horse on one body region (BR-096). Never deleted (BR-103). */
@Entity
@Table(name = "injury_mark")
public class InjuryMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "examination_id", nullable = false)
    private Long examinationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", nullable = false)
    private BodyRegion region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarkStatus status;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InjuryMark() {
    }

    public InjuryMark(Long horseId, Long examinationId, BodyRegion region, UserAccount createdBy, Instant createdAt) {
        this.horseId = horseId;
        this.examinationId = examinationId;
        this.region = region;
        this.status = MarkStatus.NEW;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void moveTo(MarkStatus next) {
        this.status = next;
    }

    public Long getId() { return id; }
    public Long getHorseId() { return horseId; }
    public Long getExaminationId() { return examinationId; }
    public BodyRegion getRegion() { return region; }
    public MarkStatus getStatus() { return status; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
