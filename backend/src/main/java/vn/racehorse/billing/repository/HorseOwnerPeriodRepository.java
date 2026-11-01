package vn.racehorse.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.billing.entity.HorseOwnerPeriod;

import java.util.List;
import java.util.Optional;

public interface HorseOwnerPeriodRepository extends JpaRepository<HorseOwnerPeriod, Long> {

    Optional<HorseOwnerPeriod> findByHorseIdAndToDateIsNull(Long horseId);

    List<HorseOwnerPeriod> findByHorseIdOrderByFromDateAsc(Long horseId);
}
