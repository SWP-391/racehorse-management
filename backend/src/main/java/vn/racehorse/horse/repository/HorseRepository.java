package vn.racehorse.horse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.horse.entity.Horse;

import java.util.List;
import java.util.Optional;

public interface HorseRepository extends JpaRepository<Horse, Long> {

    Optional<Horse> findByRegistrationNumber(String registrationNumber);

    List<Horse> findAllByOrderByNameAsc();

    /** Locks the horse row so concurrent saves of one horse's ration take turns (TECH-100). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Horse h where h.id = :id")
    Optional<Horse> findByIdForUpdate(@Param("id") Long id);

    List<Horse> findByOwnerIdOrderByNameAsc(Long ownerId);

    /** Horses a Head Trainer is the main or the temporary trainer of; the date rule is applied by the caller. */
    @Query("select h from Horse h where h.mainTrainer.id = :trainerId or h.tempTrainer.id = :trainerId")
    List<Horse> assignedTo(@Param("trainerId") Long trainerId);

    /** Loans whose return date has come (BR-156). */
    List<Horse> findByTempUntilLessThanEqual(java.time.LocalDate date);

    /** The horse holding a stall, matching the unique index (BR-129, BR-164). */
    @Query("select h from Horse h where h.area.id = :areaId and h.stallSlot = :slot")
    List<Horse> findByStall(@Param("areaId") Long areaId, @Param("slot") int slot);

    @Query("select h from Horse h where h.area.id = :areaId order by h.stallSlot")
    List<Horse> findByAreaId(@Param("areaId") Long areaId);

    @Query("select count(h) from Horse h where h.area.id = :areaId")
    long countByAreaId(@Param("areaId") Long areaId);
}
