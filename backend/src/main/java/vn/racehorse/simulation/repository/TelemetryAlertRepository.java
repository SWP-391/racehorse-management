package vn.racehorse.simulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.simulation.entity.Measure;
import vn.racehorse.simulation.entity.TelemetryAlert;

import java.util.List;

public interface TelemetryAlertRepository extends JpaRepository<TelemetryAlert, Long> {

    List<TelemetryAlert> findByHorseIdOrderByRaisedAtDescIdDesc(Long horseId);

    boolean existsBySessionIdAndMeasure(Long sessionId, Measure measure);
}
