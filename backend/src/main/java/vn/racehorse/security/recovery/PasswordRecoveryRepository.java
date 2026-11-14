package vn.racehorse.security.recovery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordRecoveryRepository extends JpaRepository<PasswordRecovery, Long> {

    /** The live code of an account, locked so a confirmation and a new request take turns. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PasswordRecovery r where r.accountId = :accountId and r.state = vn.racehorse.security.recovery.RecoveryState.LIVE")
    Optional<PasswordRecovery> findLiveForUpdate(@Param("accountId") Long accountId);

    /** Voids the live code of an account, if any; flushes first so the unique index on live codes is respected. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordRecovery r set r.state = vn.racehorse.security.recovery.RecoveryState.VOID "
            + "where r.accountId = :accountId and r.state = vn.racehorse.security.recovery.RecoveryState.LIVE")
    int voidLive(@Param("accountId") Long accountId);

    @Modifying
    @Query("delete from PasswordRecovery r where r.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
