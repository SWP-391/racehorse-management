package vn.racehorse.stable.dto;

public record BackfillRequest(Long horseId, String chore, String date, String reason) {
}
