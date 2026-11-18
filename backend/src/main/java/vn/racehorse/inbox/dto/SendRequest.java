package vn.racehorse.inbox.dto;

import jakarta.validation.constraints.NotBlank;

/** Answer in a conversation (BR-227). */
public record SendRequest(
        @NotBlank(message = "Bắt buộc nhập nội dung.") String text) {
}
