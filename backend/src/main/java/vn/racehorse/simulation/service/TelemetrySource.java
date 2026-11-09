package vn.racehorse.simulation.service;

import vn.racehorse.training.service.TrainingEnums.DataSource;

import java.time.Instant;

/**
 * Where heart rate and speed come from (TECH-003). The PoC uses {@link SimulatedTelemetrySource}; a device integration
 * later implements this interface and returns {@link DataSource#DEVICE}, with nothing else changing.
 */
public interface TelemetrySource {

    record Sample(int offsetSecond, int heartRate, double speed) {
    }

    DataSource dataSource();

    /** The sample for one second of a session. Must be repeatable for the same arguments. */
    Sample sample(long sessionId, Instant startedAt, int offsetSecond);
}
