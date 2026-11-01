package vn.racehorse.training.dto;

import jakarta.validation.Valid;

import java.util.List;

public record PlanRequest(String title, List<@Valid PlanPhaseInput> phases) {
}
