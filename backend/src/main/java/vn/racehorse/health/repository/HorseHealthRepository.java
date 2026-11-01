package vn.racehorse.health.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.health.entity.HorseHealth;

import java.util.Optional;

public interface HorseHealthRepository extends JpaRepository<HorseHealth, Long> {

    /** Serialises every decision about whether a horse may train: locks, status changes and session starts (TECH-026). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HorseHealth h where h.horseId = :horseId")
    Optional<HorseHealth> findForUpdate(@Param("horseId") Long horseId);
}
