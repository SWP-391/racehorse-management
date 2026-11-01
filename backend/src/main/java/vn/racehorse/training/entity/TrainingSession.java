package vn.racehorse.training.entity;

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
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.training.service.TrainingEnums.AssignmentAck;
import vn.racehorse.training.service.TrainingEnums.DataSource;
import vn.racehorse.training.service.TrainingEnums.Intensity;
import vn.racehorse.training.service.TrainingEnums.SessionState;
import vn.racehorse.training.service.TrainingEnums.SessionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** A training session: its plan, schedule, state, flags and recorded indicators. Never deleted (DEC-003). */
@Entity
@Table(name = "training_session")
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    /** Club-local planned start; null for sessions created before start times existed (BR-120, TECH-082). */
    @Column(name = "planned_start_time")
    private LocalTime plannedStartTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false)
    private SessionType type;

    @Column(name = "planned_duration_min", nullable = false)
    private int plannedDurationMin;

    @Column(name = "target_distance_m")
    private Integer targetDistanceM;

    @Column(name = "target_surface")
    private String targetSurface;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "responsible_person_id")
    private UserAccount responsiblePerson;

    /** Null when the responsible person is not a Groom (Q-046, TECH-061). */
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_ack")
    private AssignmentAck assignmentAck;

    @Column(name = "assignment_ack_at")
    private Instant assignmentAckAt;

    /** The last refusal by a Groom, cleared when a new person is assigned; the full history is the audit log (BR-159). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "declined_by_id")
    private UserAccount declinedBy;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "declined_at")
    private Instant declinedAt;

    /** Once-only markers of the reminder job, reset by every new assignment (BR-160, TECH-135). */
    @Column(name = "groom_reminded_at")
    private Instant groomRemindedAt;

    @Column(name = "trainer_warned_at")
    private Instant trainerWarnedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionState state;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_time_automatic", nullable = false)
    private boolean endTimeAutomatic;

    @Column(nullable = false)
    private boolean overtime;

    @Column(name = "lock_during_session", nullable = false)
    private boolean lockDuringSession;

    @Column(name = "missing_indicators", nullable = false)
    private boolean missingIndicators;

    @Column(name = "early_end_reason")
    private String earlyEndReason;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "duration_min")
    private Integer durationMin;

    private String surface;

    @Enumerated(EnumType.STRING)
    private Intensity intensity;

    private Short form;

    @Column(name = "avg_heart_rate")
    private Integer avgHeartRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "avg_heart_rate_source")
    private DataSource avgHeartRateSource;

    @Column(name = "max_speed")
    private BigDecimal maxSpeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_speed_source")
    private DataSource maxSpeedSource;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "trainer_comment")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingSession() {
    }

    public TrainingSession(Long planId, Long horseId, LocalDate date, LocalTime startTime, SessionType type,
                    int plannedDurationMin, Integer targetDistanceM, String targetSurface,
                    UserAccount responsiblePerson, SessionState state, Instant now) {
        this.planId = planId;
        this.horseId = horseId;
        this.scheduledDate = date;
        this.plannedStartTime = startTime;
        this.type = type;
        this.plannedDurationMin = plannedDurationMin;
        this.targetDistanceM = targetDistanceM;
        this.targetSurface = targetSurface;
        this.responsiblePerson = responsiblePerson;
        this.assignmentAck = responsiblePerson != null && responsiblePerson.getRole() == Role.GROOM
                ? AssignmentAck.UNCONFIRMED : null;
        this.state = state;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Indicators recorded at the end of a session (BR-036). */
    public record Indicators(Integer distanceM, Integer durationMin, String surface, Intensity intensity, Short form,
                             Integer avgHeartRate, BigDecimal maxSpeed, BigDecimal weightKg, String comment) {
        boolean mandatoryComplete() {
            return distanceM != null && durationMin != null && surface != null && !surface.isBlank()
                    && intensity != null && form != null;
        }
    }

    public void moveTo(SessionState state, Instant now) {
        this.state = state;
        this.updatedAt = now;
    }

    public void start(Instant at) {
        this.state = SessionState.IN_PROGRESS;
        this.startedAt = at;
        this.updatedAt = at;
    }

    public void complete(Indicators i, String earlyEndReason, Instant at) {
        record(i, at);
        this.state = SessionState.COMPLETED;
        this.endedAt = at;
        this.earlyEndReason = earlyEndReason;
        this.missingIndicators = false;
    }

    public void autoClose(Instant at) {
        this.state = SessionState.AUTO_CLOSED;
        this.endedAt = at;
        this.endTimeAutomatic = true;
        this.missingIndicators = !currentIndicators().mandatoryComplete();
        this.updatedAt = at;
    }

    /** After auto-close: the Head Trainer completes indicators and corrects the end time (BR-038, AC-049). */
    public void completeAfterAutoClose(Indicators i, Instant actualEnd, Instant now) {
        record(i, now);
        if (actualEnd != null) {
            this.endedAt = actualEnd;
            this.endTimeAutomatic = false;
        }
        this.missingIndicators = !currentIndicators().mandatoryComplete();
    }

    public void markOvertime(Instant now) {
        this.overtime = true;
        this.updatedAt = now;
    }

    public void markLockDuringSession(Instant now) {
        this.lockDuringSession = true;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        this.state = SessionState.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = now;
    }

    public void reschedule(LocalDate date, Instant now) {
        this.scheduledDate = date;
        this.updatedAt = now;
    }

    /** A new responsible person starts at Chưa xác nhận when a Groom, without a state otherwise (BR-118, ST-066). */
    public void assign(UserAccount person, Instant now) {
        this.responsiblePerson = person;
        this.assignmentAck = person.getRole() == Role.GROOM ? AssignmentAck.UNCONFIRMED : null;
        this.assignmentAckAt = null;
        this.declinedBy = null;
        this.declineReason = null;
        this.declinedAt = null;
        this.groomRemindedAt = null;
        this.trainerWarnedAt = null;
        this.updatedAt = now;
    }

    /** The responsible Groom refuses: nobody is responsible until the Head Trainer names someone (BR-159). */
    public void decline(UserAccount by, String reason, Instant now) {
        this.responsiblePerson = null;
        this.assignmentAck = null;
        this.assignmentAckAt = null;
        this.declinedBy = by;
        this.declineReason = reason;
        this.declinedAt = now;
        this.groomRemindedAt = null;
        this.trainerWarnedAt = null;
        this.updatedAt = now;
    }

    public void markGroomReminded(Instant now) {
        this.groomRemindedAt = now;
    }

    public void markTrainerWarned(Instant now) {
        this.trainerWarnedAt = now;
    }

    public void acknowledge(Instant now) {
        this.assignmentAck = AssignmentAck.ACKNOWLEDGED;
        this.assignmentAckAt = now;
        this.updatedAt = now;
    }

    private void record(Indicators i, Instant now) {
        this.distanceM = i.distanceM();
        this.durationMin = i.durationMin();
        this.surface = i.surface();
        this.intensity = i.intensity();
        this.form = i.form();
        if (i.avgHeartRate() != null || avgHeartRateSource != DataSource.SIMULATED) {
            this.avgHeartRate = i.avgHeartRate();
            this.avgHeartRateSource = i.avgHeartRate() == null ? null : DataSource.MANUAL;
        }
        if (i.maxSpeed() != null || maxSpeedSource != DataSource.SIMULATED) {
            this.maxSpeed = i.maxSpeed();
            this.maxSpeedSource = i.maxSpeed() == null ? null : DataSource.MANUAL;
        }
        this.weightKg = i.weightKg();
        this.comment = i.comment() == null || i.comment().isBlank() ? null : i.comment().trim();
        this.updatedAt = now;
    }

    /**
     * Values filled by the simulated feed at session end (BR-051, TECH-040): only where the Head Trainer entered
     * nothing, so a manual value stays manual and the sources are never mixed within one indicator.
     */
    public void fillSimulated(Integer avgHeartRate, BigDecimal maxSpeed) {
        if (this.avgHeartRate == null && avgHeartRate != null) {
            this.avgHeartRate = avgHeartRate;
            this.avgHeartRateSource = DataSource.SIMULATED;
        }
        if (this.maxSpeed == null && maxSpeed != null) {
            this.maxSpeed = maxSpeed;
            this.maxSpeedSource = DataSource.SIMULATED;
        }
    }

    Indicators currentIndicators() {
        return new Indicators(distanceM, durationMin, surface, intensity, form, avgHeartRate, maxSpeed, weightKg,
                comment);
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public Long getHorseId() { return horseId; }
    public LocalDate getScheduledDate() { return scheduledDate; }

    public LocalTime getPlannedStartTime() { return plannedStartTime; }

    /** Club-local planned start, or null when the session has no start time (TECH-082). */
    public LocalDateTime plannedStart() {
        return plannedStartTime == null ? null : scheduledDate.atTime(plannedStartTime);
    }
    public SessionType getType() { return type; }
    public int getPlannedDurationMin() { return plannedDurationMin; }
    public Integer getTargetDistanceM() { return targetDistanceM; }
    public String getTargetSurface() { return targetSurface; }
    public UserAccount getResponsiblePerson() { return responsiblePerson; }
    public SessionState getState() { return state; }

    public AssignmentAck getAssignmentAck() { return assignmentAck; }

    public Instant getAssignmentAckAt() { return assignmentAckAt; }
    public UserAccount getDeclinedBy() { return declinedBy; }
    public String getDeclineReason() { return declineReason; }
    public Instant getDeclinedAt() { return declinedAt; }
    public Instant getGroomRemindedAt() { return groomRemindedAt; }
    public Instant getTrainerWarnedAt() { return trainerWarnedAt; }

    /** Upcoming, not a draft and nobody responsible: the flag "Cần người phụ trách" (BR-159, derived, never stored). */
    public boolean needsResponsible() {
        return responsiblePerson == null && state != SessionState.DRAFT && state.isUpcoming();
    }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public boolean isEndTimeAutomatic() { return endTimeAutomatic; }
    public boolean isOvertime() { return overtime; }
    public boolean isLockDuringSession() { return lockDuringSession; }
    public boolean isMissingIndicators() { return missingIndicators; }
    public String getEarlyEndReason() { return earlyEndReason; }
    public String getCancelReason() { return cancelReason; }
    public Integer getDistanceM() { return distanceM; }
    public Integer getDurationMin() { return durationMin; }
    public String getSurface() { return surface; }
    public Intensity getIntensity() { return intensity; }
    public Short getForm() { return form; }
    public Integer getAvgHeartRate() { return avgHeartRate; }
    public DataSource getAvgHeartRateSource() { return avgHeartRateSource; }
    public BigDecimal getMaxSpeed() { return maxSpeed; }
    public DataSource getMaxSpeedSource() { return maxSpeedSource; }
    public BigDecimal getWeightKg() { return weightKg; }
    public String getComment() { return comment; }
}
