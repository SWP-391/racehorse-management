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

import java.math.BigDecimal;
import java.time.LocalDate;

/** A plan phase: name chosen by the Head Trainer, dates, goal, distance (m), weekly volume (km), surface (DEC-005, DEC-026). */
@Entity
@Table(name = "plan_phase")
public class PlanPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private TrainingPlan plan;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private String goal;

    @Column(name = "distance_m", nullable = false)
    private int distanceM;

    @Column(name = "weekly_volume_km", nullable = false)
    private BigDecimal weeklyVolumeKm;

    @Column(nullable = false)
    private String surface;

    protected PlanPhase() {
    }

    public PlanPhase(int ordinal, String name, LocalDate startDate, LocalDate endDate, String goal, int distanceM,
              BigDecimal weeklyVolumeKm, String surface) {
        this.ordinal = ordinal;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.goal = goal;
        this.distanceM = distanceM;
        this.weeklyVolumeKm = weeklyVolumeKm;
        this.surface = surface;
    }

    void attach(TrainingPlan plan) {
        this.plan = plan;
    }

    public void shift(int days) {
        startDate = startDate.plusDays(days);
        endDate = endDate.plusDays(days);
    }

    public void setDates(LocalDate start, LocalDate end) {
        startDate = start;
        endDate = end;
    }

    public Long getId() {
        return id;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getGoal() {
        return goal;
    }

    public int getDistanceM() {
        return distanceM;
    }

    public BigDecimal getWeeklyVolumeKm() {
        return weeklyVolumeKm;
    }

    public String getSurface() {
        return surface;
    }
}
