package vn.racehorse.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.audit.entity.AuditEntry;

import java.time.Instant;
import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    @Query("""
            select e from AuditEntry e
            where e.occurredAt >= :from and e.occurredAt < :to
              and (:actorId is null or e.actorId = :actorId)
            order by e.occurredAt desc, e.id desc
            """)
    List<AuditEntry> search(@Param("from") Instant from, @Param("to") Instant to,
                            @Param("actorId") Long actorId, Pageable page);

    /**
     * One page of the entries in the range. The text search is resolved by the caller into the actor names, action codes
     * and target types it matches, so paging and the exact total stay in the database (BR-185).
     */
    @Query("""
            select e from AuditEntry e
            where e.occurredAt >= :from and e.occurredAt < :to
              and (:actorId is null or e.actorId = :actorId)
              and (:action = '' or e.action = :action)
              and (:all = true or e.actorName in :names or e.action in :actions or e.targetType in :targets)
            order by e.occurredAt desc, e.id desc
            """)
    Page<AuditEntry> page(@Param("from") Instant from, @Param("to") Instant to, @Param("actorId") Long actorId,
                          @Param("action") String action, @Param("all") boolean all,
                          @Param("names") List<String> names, @Param("actions") List<String> actions,
                          @Param("targets") List<String> targets, Pageable page);

    @Query("select distinct e.actorName from AuditEntry e where e.actorName is not null")
    List<String> actorNames();

    @Query("select distinct e.action from AuditEntry e")
    List<String> actions();

    @Query("select distinct e.targetType from AuditEntry e where e.targetType is not null")
    List<String> targetTypes();
}
