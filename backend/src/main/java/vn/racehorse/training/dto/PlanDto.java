package vn.racehorse.training.dto;

import vn.racehorse.training.service.TrainingEnums.PlanState;

import java.util.List;

public record PlanDto(Long id, Long horseId, String title, PlanState state, String cancelReason,
                      boolean completionSuggested, List<PhaseDto> phases, PersonRef assignedTrainer,
                      boolean canWrite) {
}
