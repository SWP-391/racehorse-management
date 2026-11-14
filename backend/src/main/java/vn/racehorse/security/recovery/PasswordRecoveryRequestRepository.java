package vn.racehorse.security.recovery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PasswordRecoveryRequestRepository extends JpaRepository<PasswordRecoveryRequest, Long> {

    /** When the address asked since the given time, oldest first. */
    @Query("select r.requestedAt from PasswordRecoveryRequest r where r.email = :email and r.requestedAt > :since "
            + "order by r.requestedAt asc")
    List<Instant> requestTimesSince(@Param("email") String email, @Param("since") Instant since);

    /** Makes requests for one address take turns until the transaction ends, so the pace cannot be raced. */
    @Query(value = "select cast(pg_advisory_xact_lock(hashtext(:email)) as text)", nativeQuery = true)
    String lockAddress(@Param("email") String email);

    @Modifying
    @Query("delete from PasswordRecoveryRequest r where r.requestedAt < :before")
    int deleteRequestedBefore(@Param("before") Instant before);
}
