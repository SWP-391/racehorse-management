package vn.racehorse.video.dto;

import org.springframework.core.io.Resource;
import vn.racehorse.video.entity.TrialVideo;

public record TrialVideoContent(TrialVideo video, Resource resource) {
}
