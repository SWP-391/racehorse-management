package vn.racehorse.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.health.entity.PendingLockRequest;

import java.util.Optional;

public interface PendingLockRequestRepository extends JpaRepository<PendingLockRequest, Long> {

    Optional<PendingLockRequest> findByHorseIdAndWithdrawnAtIsNullAndAppliedAtIsNull(Long horseId);
}
