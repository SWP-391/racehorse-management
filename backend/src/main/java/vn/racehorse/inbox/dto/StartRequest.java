package vn.racehorse.inbox.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Start a conversation (BR-227, BR-228). The recipient is a person ({@code recipientId}) or the Club Manager role
 * ({@code toClubManagers}); exactly one of the two.
 */
public record StartRequest(
        @NotBlank(message = "Bắt buộc nhập tiêu đề.") String subject,
        @NotBlank(message = "Bắt buộc nhập nội dung.") String text,
        Long horseId,
        Long recipientId,
        Boolean toClubManagers,
        String kind) {
}
