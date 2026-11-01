package vn.racehorse.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.health.entity.TrainingLock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TrainingLockRepository extends JpaRepository<TrainingLock, Long> {

    Optional<TrainingLock> findByHorseIdAndLiftedAtIsNull(Long horseId);

    List<TrainingLock> findByHorseIdInAndLiftedAtIsNull(Collection<Long> horseIds);
}
