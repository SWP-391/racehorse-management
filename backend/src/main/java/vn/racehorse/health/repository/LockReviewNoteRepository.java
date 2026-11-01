package vn.racehorse.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.health.entity.LockReviewNote;

import java.util.List;

public interface LockReviewNoteRepository extends JpaRepository<LockReviewNote, Long> {

    List<LockReviewNote> findByLockIdOrderByCreatedAtAsc(Long lockId);
}
