package vn.racehorse.security.recovery;

import java.time.Duration;

/** Why a code was issued; it sets how long the code lives and which mail carries it (BR-215, BR-237, TECH-233). */
public enum RecoveryPurpose {

    RESET(Duration.ofMinutes(5)),
    ACTIVATION(Duration.ofHours(24));

    private final Duration ttl;

    RecoveryPurpose(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }
}
