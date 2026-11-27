package vn.racehorse.security.recovery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CodeTokenRepository extends JpaRepository<CodeToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodeToken> findByTokenHash(String tokenHash);

    /** Ends every unused token of a person, so only the newest one of step 1 can finish step 2. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CodeToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int useUp(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from CodeToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
