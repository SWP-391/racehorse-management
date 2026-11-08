package vn.racehorse.simulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.simulation.entity.Measure;
import vn.racehorse.simulation.entity.TelemetryThreshold;

import java.util.List;
import java.util.Optional;

public interface TelemetryThresholdRepository extends JpaRepository<TelemetryThreshold, Long> {

    List<TelemetryThreshold> findByHorseIdOrderByMeasureAsc(Long horseId);

    Optional<TelemetryThreshold> findByHorseIdAndMeasure(Long horseId, Measure measure);
}
