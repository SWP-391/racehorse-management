package vn.racehorse.health.entity;

/** Race readiness shown to the Owner, derived from status and lock (BR-080, DEC-024). */
public enum Readiness {
    /** Sẵn sàng */
    READY,
    /** Cần theo dõi */
    UNDER_OBSERVATION,
    /** Không sẵn sàng */
    NOT_READY;

    public static Readiness of(HealthStatus status, boolean activeLock) {
        if (activeLock) {
            return NOT_READY;
        }
        if (status == HealthStatus.UNDER_OBSERVATION) {
            return UNDER_OBSERVATION;
        }
        return status == HealthStatus.ELIGIBLE ? READY : NOT_READY;
    }
}
