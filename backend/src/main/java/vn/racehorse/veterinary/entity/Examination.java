package vn.racehorse.veterinary.entity;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** An examination record (BR-060). Never updated or deleted. */
@Entity
@Table(name = "examination")
public class Examination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(nullable = false)
    private String diagnosis;

    /** Free text of examinations recorded before the shared catalog (DEC-036); kept unchanged, never written now. */
    @Column(name = "body_area")
    private String bodyArea;

    /** Body area from the one catalog (BR-060, BR-110). */
    @Enumerated(EnumType.STRING)
    @Column(name = "body_region")
    private BodyRegion bodyRegion;

    @Column(name = "body_detail")
    private String bodyDetail;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    /** The kind of examination the fee was taken from; empty on records made before the price list (BR-060). */
    @Column(name = "fee_type_id")
    private Long feeTypeId;

    private String treatment;

    private String prescription;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "recorded_by_id", nullable = false)
    private UserAccount recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected Examination() {
    }

    public Examination(Long horseId, LocalDate examDate, String diagnosis, BodyRegion bodyRegion, String bodyDetail,
                BigDecimal weightKg, String treatment, String prescription, UserAccount recordedBy,
                Instant recordedAt) {
        this.horseId = horseId;
        this.examDate = examDate;
        this.diagnosis = diagnosis;
        this.bodyRegion = bodyRegion;
        this.bodyDetail = bodyDetail;
        this.weightKg = weightKg;
        this.treatment = treatment;
        this.prescription = prescription;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public Long getHorseId() { return horseId; }
    public LocalDate getExamDate() { return examDate; }
    public String getDiagnosis() { return diagnosis; }
    public String getBodyArea() { return bodyArea; }
    public BodyRegion getBodyRegion() { return bodyRegion; }
    public String getBodyDetail() { return bodyDetail; }
    public BigDecimal getWeightKg() { return weightKg; }
    public String getTreatment() { return treatment; }
    public String getPrescription() { return prescription; }
    public UserAccount getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
    public Long getFeeTypeId() { return feeTypeId; }

    public void chargeFeeType(Long feeTypeId) {
        this.feeTypeId = feeTypeId;
    }
}
