package vn.racehorse.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.service.TrainingEnums.SessionState;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {

    List<TrainingSession> findByPlanIdOrderByScheduledDateAscIdAsc(Long planId);

    List<TrainingSession> findByHorseIdOrderByScheduledDateDescIdDesc(Long horseId);

    List<TrainingSession> findByResponsiblePersonIdOrderByScheduledDateDescIdDesc(Long personId);

    List<TrainingSession> findByResponsiblePersonIdAndScheduledDateBetween(Long personId, LocalDate from, LocalDate to);

    List<TrainingSession> findByHorseIdInAndScheduledDateBetween(Collection<Long> horseIds, LocalDate from, LocalDate to);

    List<TrainingSession> findByHorseIdAndStateIn(Long horseId, Collection<SessionState> states);

    Optional<TrainingSession> findFirstByHorseIdAndState(Long horseId, SessionState state);

    List<TrainingSession> findByState(SessionState state);

    List<TrainingSession> findByStateInAndScheduledDateBefore(Collection<SessionState> states, LocalDate date);
}
