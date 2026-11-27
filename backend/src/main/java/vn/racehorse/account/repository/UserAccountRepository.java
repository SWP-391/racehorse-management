package vn.racehorse.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    /** Locks one account row so concurrent role changes and deactivations of it take turns (TECH-121). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from UserAccount a where a.id = :id")
    Optional<UserAccount> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByEmail(String email);

    boolean existsByRoleAndActiveTrue(Role role);

    /**
     * Locks all active Club Managers, in id order, so two requests that would together remove the last one take
     * turns and the second one sees the first one's result (TECH-121, P-06).
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select a from UserAccount a where a.role = vn.racehorse.account.entity.Role.CLUB_MANAGER and a.active = true order by a.id")
    List<UserAccount> lockActiveClubManagers();

    List<UserAccount> findAllByOrderByDisplayNameAsc();

    List<UserAccount> findByRoleOrderByDisplayNameAsc(Role role);

    /** Names of the horses (not archived) a person holds as main or temporary trainer (BR-252). */
    @org.springframework.data.jpa.repository.Query(value =
            "select name from horse where archived_at is null and intake_state = 'ACCEPTED' and (main_trainer_id = :id or temp_trainer_id = :id) order by name",
            nativeQuery = true)
    List<String> horseNamesHeldAsTrainer(@org.springframework.data.repository.query.Param("id") Long id);

    /** Names of the horses (not archived, taken in) a Veterinarian holds as main or substitute (BR-259). */
    @org.springframework.data.jpa.repository.Query(value =
            "select name from horse where archived_at is null and intake_state = 'ACCEPTED' and (vet_id = :id or temp_vet_id = :id) order by name",
            nativeQuery = true)
    List<String> horseNamesHeldAsVet(@org.springframework.data.repository.query.Param("id") Long id);

    /** Names of the horses (not archived, taken in) a Groom holds as main or substitute (BR-259). */
    @org.springframework.data.jpa.repository.Query(value =
            "select name from horse where archived_at is null and intake_state = 'ACCEPTED' and (groom_id = :id or temp_groom_id = :id) order by name",
            nativeQuery = true)
    List<String> horseNamesHeldAsGroom(@org.springframework.data.repository.query.Param("id") Long id);
}
