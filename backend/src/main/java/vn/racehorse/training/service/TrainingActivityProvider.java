package vn.racehorse.training.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.racehorse.health.entity.ActivityStatus;
import vn.racehorse.health.service.ActivityStatusProvider;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.repository.TrainingSessionRepository;
import vn.racehorse.training.service.TrainingEnums.SessionState;

import java.util.Optional;

/** A horse is Đang tập while one of its sessions is in progress (DEC-004, TECH-024). */
@Primary
@Component
class TrainingActivityProvider implements ActivityStatusProvider {

    private final TrainingSessionRepository sessions;

    TrainingActivityProvider(TrainingSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public ActivityStatus activityOf(Long horseId) {
        return sessionInProgress(horseId).isPresent() ? ActivityStatus.TRAINING : ActivityStatus.IDLE;
    }

    @Override
    public Optional<Long> sessionInProgress(Long horseId) {
        return sessions.findFirstByHorseIdAndState(horseId, SessionState.IN_PROGRESS).map(TrainingSession::getId);
    }
}
