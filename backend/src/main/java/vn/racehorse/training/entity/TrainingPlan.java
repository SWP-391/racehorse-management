package vn.racehorse.training.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.training.service.TrainingEnums.PlanState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "training_plan")
public class TrainingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanState state;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordinal ASC")
    private List<PlanPhase> phases = new ArrayList<>();

    protected TrainingPlan() {
    }

    public TrainingPlan(Long horseId, String title, UserAccount createdBy, Instant now) {
        this.horseId = horseId;
        this.title = title;
        this.state = PlanState.DRAFT;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void replacePhases(List<PlanPhase> next, Instant now) {
        phases.clear();
        for (PlanPhase p : next) {
            p.attach(this);
            phases.add(p);
        }
        updatedAt = now;
    }

    public void setTitle(String title, Instant now) {
        this.title = title;
        this.updatedAt = now;
    }

    public void moveTo(PlanState state, Instant now) {
        this.state = state;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        this.state = PlanState.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = now;
    }

    /** The system suggests completion once the last phase has ended (BR-031); it never completes the plan itself. */
    public boolean completionSuggested(LocalDate today) {
        return state == PlanState.ACTIVE && !phases.isEmpty()
                && phases.get(phases.size() - 1).getEndDate().isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public Long getHorseId() {
        return horseId;
    }

    public String getTitle() {
        return title;
    }

    public PlanState getState() {
        return state;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    public List<PlanPhase> getPhases() {
        return phases;
    }
}
