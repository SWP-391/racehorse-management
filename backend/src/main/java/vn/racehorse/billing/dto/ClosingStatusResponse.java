package vn.racehorse.billing.dto;

/** The closing state of a month; `openMonth` names the earlier month still open, `message` is the refusal text. */
public record ClosingStatusResponse(String month, ClosingState state, String openMonth, String message) {
}
