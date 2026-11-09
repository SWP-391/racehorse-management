package vn.racehorse.simulation.service;

import org.springframework.stereotype.Component;
import vn.racehorse.training.service.TrainingEnums.DataSource;

import java.time.Instant;

/**
 * Deterministic simulated telemetry (TECH-003): a pure function of the session id and the second, so the live view,
 * alerts and the end-of-session summary always agree and tests are exact. Always labelled as simulated (BR-050).
 *
 * <p>Heart rate rises from about 45 bpm towards 190 bpm (time constant 120 s); speed from 0 towards 55 km/h (90 s);
 * both with a small repeatable wobble.
 */
@Component
public class SimulatedTelemetrySource implements TelemetrySource {

    static final double REST_BPM = 45;
    static final double PEAK_BPM = 190;
    static final double PEAK_KMH = 55;

    @Override
    public DataSource dataSource() {
        return DataSource.SIMULATED;
    }

    @Override
    public Sample sample(long sessionId, Instant startedAt, int offsetSecond) {
        int t = Math.max(0, offsetSecond);
        double hr = REST_BPM + (PEAK_BPM - REST_BPM) * (1 - Math.exp(-t / 120.0)) + wobble(sessionId, t, 1) * 6;
        double speed = PEAK_KMH * (1 - Math.exp(-t / 90.0)) + wobble(sessionId, t, 2) * 1.5;
        return new Sample(t, (int) Math.round(Math.max(30, hr)), Math.round(Math.max(0, speed) * 10) / 10.0);
    }

    /** A repeatable value in [-1, 1] from the session, the second and a channel number. */
    static double wobble(long sessionId, int second, int channel) {
        long h = sessionId * 0x9E3779B97F4A7C15L + second * 0xC2B2AE3D27D4EB4FL + channel * 0x165667B19E3779F9L;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        return ((h & 0xFFFF) / 65535.0) * 2 - 1;
    }
}
