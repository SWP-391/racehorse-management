package vn.racehorse.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.training.entity.PlanVersion;

import java.util.List;
import java.util.Optional;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, Long> {

    List<PlanVersion> findByPlanIdOrderByVersionNoAsc(Long planId);

    Optional<PlanVersion> findByPlanIdAndVersionNo(Long planId, int versionNo);

    Optional<PlanVersion> findFirstByPlanIdOrderByVersionNoDesc(Long planId);
}
