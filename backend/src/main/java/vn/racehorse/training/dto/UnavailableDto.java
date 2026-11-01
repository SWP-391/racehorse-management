package vn.racehorse.training.dto;

import vn.racehorse.training.service.Unavailable;

public record UnavailableDto(Long id, String displayName, Unavailable.Reason reason, String detail,
                             Long sessionId) {
}
