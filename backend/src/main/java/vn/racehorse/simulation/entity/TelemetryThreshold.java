package vn.racehorse.simulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** An alert limit for one horse and measure, set by the Head Trainer; there is no default (BR-052). */
@Entity
@Table(name = "telemetry_threshold")
public class TelemetryThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Measure measure;

    @Column(name = "limit_value", nullable = false)
    private BigDecimal limit;

    @Column(name = "set_by_id", nullable = false)
    private Long setById;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    protected TelemetryThreshold() {
    }

    public TelemetryThreshold(Long horseId, Measure measure, BigDecimal limit, Long setById, Instant setAt) {
        this.horseId = horseId;
        this.measure = measure;
        this.limit = limit;
        this.setById = setById;
        this.setAt = setAt;
    }

    public void change(BigDecimal limit, Long setById, Instant setAt) {
        this.limit = limit;
        this.setById = setById;
        this.setAt = setAt;
    }

    public Long getHorseId() { return horseId; }
    public Measure getMeasure() { return measure; }
    public BigDecimal getLimit() { return limit; }
    public Instant getSetAt() { return setAt; }
}
