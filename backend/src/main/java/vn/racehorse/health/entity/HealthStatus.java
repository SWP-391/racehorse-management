package vn.racehorse.health.entity;

import java.util.EnumSet;
import java.util.Set;

/** Veterinarian-owned health status (BR-010) and the transitions allowed from each (BR-012, ST-001 to ST-009). */
public enum HealthStatus {
    /** Đủ điều kiện */
    ELIGIBLE,
    /** Cần theo dõi */
    UNDER_OBSERVATION,
    /** Chấn thương */
    INJURED,
    /** Cách ly */
    QUARANTINED;

    public Set<HealthStatus> allowedNext() {
        return switch (this) {
            case ELIGIBLE -> EnumSet.of(UNDER_OBSERVATION, INJURED, QUARANTINED);
            case UNDER_OBSERVATION -> EnumSet.of(ELIGIBLE, INJURED, QUARANTINED);
            case INJURED -> EnumSet.of(QUARANTINED, UNDER_OBSERVATION);
            case QUARANTINED -> EnumSet.of(INJURED, UNDER_OBSERVATION);
        };
    }

    /** Statuses that place an automatic lock and forbid lifting one (BR-014, BR-017). */
    public boolean forbidsTraining() {
        return this == INJURED || this == QUARANTINED;
    }

    public String label() {
        return switch (this) {
            case ELIGIBLE -> "Đủ điều kiện";
            case UNDER_OBSERVATION -> "Cần theo dõi";
            case INJURED -> "Chấn thương";
            case QUARANTINED -> "Cách ly";
        };
    }
}
