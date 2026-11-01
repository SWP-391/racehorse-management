package vn.racehorse.training.dto;

import java.util.List;

public record AssignableDto(List<PersonRef> available, List<UnavailableDto> unavailable, String nextFree) {
}
