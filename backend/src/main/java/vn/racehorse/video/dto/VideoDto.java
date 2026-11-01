package vn.racehorse.video.dto;

import vn.racehorse.video.entity.TrialVideo;

import java.time.Instant;

public record VideoDto(Long id, Long sessionId, Long horseId, String fileName, long sizeBytes, String uploadedBy,
                       Instant uploadedAt, String contentUrl) {
    public static VideoDto of(TrialVideo v) {
        return new VideoDto(v.getId(), v.getSessionId(), v.getHorseId(), v.getFileName(), v.getSizeBytes(),
                v.getUploadedBy().getDisplayName(), v.getUploadedAt(), "/api/videos/" + v.getId() + "/content");
    }
}
