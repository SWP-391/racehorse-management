package vn.racehorse.ration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.ration.entity.RationVersion;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RationVersionRepository extends JpaRepository<RationVersion, Long> {

    List<RationVersion> findByHorseIdOrderByVersionNoDesc(Long horseId);

    Optional<RationVersion> findFirstByHorseIdOrderByVersionNoDesc(Long horseId);

    List<RationVersion> findByHorseIdInOrderByVersionNoDesc(Collection<Long> horseIds);

    /** The version in force on a date: the latest whose effective date is not after it (BR-140, BR-173). */
    Optional<RationVersion> findFirstByHorseIdAndEffectiveDateLessThanEqualOrderByVersionNoDesc(Long horseId, LocalDate date);
}
