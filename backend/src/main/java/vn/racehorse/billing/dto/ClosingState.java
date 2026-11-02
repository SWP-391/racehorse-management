package vn.racehorse.billing.dto;

/** Why a month can or cannot be closed now (BR-205); the code is the one the closing refusal carries. */
public enum ClosingState {
    READY(null),
    CLOSED("MONTH_ALREADY_CLOSED"),
    NOT_FINISHED("MONTH_NOT_FINISHED"),
    FEES_PENDING("MONTH_NOT_READY"),
    PREVIOUS_MONTH_OPEN("PREVIOUS_MONTH_OPEN");

    private final String refusalCode;

    ClosingState(String refusalCode) {
        this.refusalCode = refusalCode;
    }

    public String refusalCode() {
        return refusalCode;
    }
}
