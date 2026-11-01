package vn.racehorse.video.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.racehorse.video.entity.TrialVideo;

import java.util.Collection;
import java.util.List;

public interface TrialVideoRepository extends JpaRepository<TrialVideo, Long> {

    List<TrialVideo> findBySessionIdOrderByIdAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    @Query("select v.sessionId, count(v) from TrialVideo v where v.sessionId in :sessionIds group by v.sessionId")
    List<Object[]> countBySessionIds(Collection<Long> sessionIds);
}
