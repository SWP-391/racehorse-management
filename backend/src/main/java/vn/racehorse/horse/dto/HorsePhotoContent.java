package vn.racehorse.horse.dto;

import org.springframework.core.io.Resource;

public record HorsePhotoContent(Resource resource, String contentType) {
}
