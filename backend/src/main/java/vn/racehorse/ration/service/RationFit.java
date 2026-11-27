package vn.racehorse.ration.service;

import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.ration.entity.RationTag;

/** The one rule of whether a ration tag fits a health status (BR-249, TECH-D2). */
public final class RationFit {

    private RationFit() {
    }

    public static boolean matches(HealthStatus status, RationTag tag) {
        if (status == null || tag == null) {
            return true;
        }
        return switch (status) {
            case ELIGIBLE -> tag == RationTag.NORMAL;
            case UNDER_OBSERVATION -> tag == RationTag.NORMAL || tag == RationTag.WATCH;
            case INJURED -> tag == RationTag.RECOVERY;
            case QUARANTINED -> tag == RationTag.ISOLATION;
        };
    }
}
