package vn.racehorse.ration.dto;

import java.util.List;

public record RationDto(Long horseId, String horseName, boolean archived, VersionDto current,
                        List<VersionDto> history) {
}
