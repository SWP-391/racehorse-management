package vn.racehorse.horse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.horse.entity.Horse;

import java.util.List;
import java.util.Optional;

public interface HorseRepository extends JpaRepository<Horse, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Horse> {

    Optional<Horse> findByRegistrationNumber(String registrationNumber);

    /**
     * THE predicate of "managed horse" (TECH-B1): only taken-in horses reach the trainer board, stalls, schedules,
     * chores, health, rations and fees. Waiting, refused and withdrawn horses are read through the registry methods.
     */
    String MANAGED = "h.intakeState = vn.racehorse.horse.entity.IntakeState.ACCEPTED";

    /** The same predicate as {@link #MANAGED}, for specifications; {@code HorseVisibility} builds on it (TECH-F3). */
    static org.springframework.data.jpa.domain.Specification<Horse> managed() {
        return (root, query, cb) -> cb.equal(root.get("intakeState"), vn.racehorse.horse.entity.IntakeState.ACCEPTED);
    }

    /** Every taken-in horse; this name is used by every module except the registry. */
    @Query("select h from Horse h where " + MANAGED + " order by h.name asc")
    List<Horse> findAllByOrderByNameAsc();

    /** Every horse in any intake state: the registry only (Club Manager list, waiting list). */
    @Query("select h from Horse h order by h.name asc")
    List<Horse> findEveryHorseByOrderByNameAsc();

    /** Every horse of an owner in any intake state: the owner's own registry view. */
    @Query("select h from Horse h where h.owner.id = :ownerId order by h.name asc")
    List<Horse> findEveryHorseOfOwner(@Param("ownerId") Long ownerId);

    @Query("select h from Horse h where " + MANAGED)
    List<Horse> findAllManaged();

    /** Locks the horse row so concurrent saves of one horse's ration take turns (TECH-100). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Horse h where h.id = :id")
    Optional<Horse> findByIdForUpdate(@Param("id") Long id);

    /** The taken-in horses of an owner; the registry reads {@link #findEveryHorseOfOwner}. */
    @Query("select h from Horse h where h.owner.id = :ownerId and " + MANAGED + " order by h.name asc")
    List<Horse> findByOwnerIdOrderByNameAsc(@Param("ownerId") Long ownerId);

    /** Horses a Head Trainer is the main or the temporary trainer of; the date rule is applied by the caller. */
    @Query("select h from Horse h where (h.mainTrainer.id = :trainerId or h.tempTrainer.id = :trainerId) and " + MANAGED)
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

    // ---- care team (BR-256 to BR-259): what counts toward the limit of 10 is the main person only (BR-257) ----

    @Query("select count(h) from Horse h where h.mainTrainer.id = :id and " + MANAGED + " and h.archivedAt is null")
    long countAsTrainer(@Param("id") Long id);

    @Query("select count(h) from Horse h where h.vet.id = :id and " + MANAGED + " and h.archivedAt is null")
    long countAsVet(@Param("id") Long id);

    @Query("select count(h) from Horse h where h.groom.id = :id and " + MANAGED + " and h.archivedAt is null")
    long countAsGroom(@Param("id") Long id);

    @Query("select h from Horse h where h.mainTrainer.id = :id and " + MANAGED + " and h.archivedAt is null order by h.name")
    List<Horse> mainOfTrainer(@Param("id") Long id);

    @Query("select h from Horse h where h.vet.id = :id and " + MANAGED + " and h.archivedAt is null order by h.name")
    List<Horse> mainOfVet(@Param("id") Long id);

    @Query("select h from Horse h where h.groom.id = :id and " + MANAGED + " and h.archivedAt is null order by h.name")
    List<Horse> mainOfGroom(@Param("id") Long id);

    /** Substitutes whose return date has come; the caller clears them (BR-258). */
    List<Horse> findByTempVetUntilLessThanEqual(java.time.LocalDate date);

    List<Horse> findByTempGroomUntilLessThanEqual(java.time.LocalDate date);

    /** Taken-in, not archived horses still without a person of some role: "Chưa gán" (BR-260). */
    @Query("select h from Horse h where " + MANAGED + " and h.archivedAt is null and "
            + "(h.mainTrainer is null or h.vet is null or h.groom is null) order by h.name")
    List<Horse> withoutFullCareTeam();
}
