package vn.racehorse.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.health.entity.HealthStatusChange;

import java.util.List;

public interface HealthStatusChangeRepository extends JpaRepository<HealthStatusChange, Long> {

    List<HealthStatusChange> findByHorseIdOrderByChangedAtDescIdDesc(Long horseId);
}
