package vn.racehorse.health.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "horse_health")
public class HorseHealth {

    @Id
    @Column(name = "horse_id")
    private Long horseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthStatus status;

    @Column(name = "first_examined_at")
    private Instant firstExaminedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HorseHealth() {
    }

    public HorseHealth(Long horseId, HealthStatus status, Instant now) {
        this.horseId = horseId;
        this.status = status;
        this.updatedAt = now;
    }

    public void changeStatus(HealthStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    /** Called by examinations (slice 4); clears the new-horse label (BR-009). */
    public void markExamined(Instant at) {
        if (firstExaminedAt == null) {
            firstExaminedAt = at;
        }
    }

    public Long getHorseId() {
        return horseId;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public Instant getFirstExaminedAt() {
        return firstExaminedAt;
    }

    public boolean isUnexamined() {
        return firstExaminedAt == null;
    }
}
