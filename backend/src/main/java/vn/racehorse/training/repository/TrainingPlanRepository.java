package vn.racehorse.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.training.entity.TrainingPlan;
import vn.racehorse.training.service.TrainingEnums.PlanState;

import java.util.List;
import java.util.Optional;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {

    List<TrainingPlan> findByHorseIdOrderByCreatedAtDesc(Long horseId);

    /** The plan Đang thực hiện of a horse, at most one (BR-154). */
    Optional<TrainingPlan> findFirstByHorseIdAndState(Long horseId, PlanState state);
}
