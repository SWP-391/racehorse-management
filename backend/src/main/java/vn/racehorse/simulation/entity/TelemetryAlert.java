package vn.racehorse.simulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import vn.racehorse.training.service.TrainingEnums.DataSource;

import java.math.BigDecimal;
import java.time.Instant;

/** A threshold crossing during a session; at most one per session and measure. Never changes status or lock (BR-052). */
@Entity
@Table(name = "telemetry_alert")
public class TelemetryAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Measure measure;

    @Column(name = "limit_value", nullable = false)
    private BigDecimal limit;

    @Column(nullable = false)
    private BigDecimal value;

    @Column(name = "offset_second", nullable = false)
    private int offsetSecond;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false)
    private DataSource dataSource;

    protected TelemetryAlert() {
    }

    public TelemetryAlert(Long sessionId, Long horseId, Measure measure, BigDecimal limit, BigDecimal value, int offsetSecond,
                   Instant raisedAt, DataSource dataSource) {
        this.sessionId = sessionId;
        this.horseId = horseId;
        this.measure = measure;
        this.limit = limit;
        this.value = value;
        this.offsetSecond = offsetSecond;
        this.raisedAt = raisedAt;
        this.dataSource = dataSource;
    }

    public Long getSessionId() { return sessionId; }
    public Long getHorseId() { return horseId; }
    public Measure getMeasure() { return measure; }
    public BigDecimal getLimit() { return limit; }
    public BigDecimal getValue() { return value; }
    public int getOffsetSecond() { return offsetSecond; }
    public Instant getRaisedAt() { return raisedAt; }
    public DataSource getDataSource() { return dataSource; }
}
