package vn.racehorse.training.dto;

import vn.racehorse.training.entity.TrainingSession;

public record AssignedSession(TrainingSession session, String horseName) {
}
