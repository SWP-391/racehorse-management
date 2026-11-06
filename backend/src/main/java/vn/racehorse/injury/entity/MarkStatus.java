package vn.racehorse.injury.entity;

/**
 * Status of an injury mark (ST-060 to ST-064): Mới, Đang hồi phục, Đã hồi phục, and Đã hủy (nhập nhầm) for a mark
 * created by mistake.
 */
public enum MarkStatus {
    NEW, RECOVERING, RECOVERED, CANCELLED;

    /**
     * Mới to Đang hồi phục, Đang hồi phục to Đã hồi phục, Mới to Đã hồi phục; nothing leaves Đã hồi phục (BR-097).
     * Mới to Đã hủy (nhập nhầm) only, and nothing leaves Đã hủy (BR-112).
     */
    public boolean canMoveTo(MarkStatus next) {
        return switch (this) {
            case NEW -> next == RECOVERING || next == RECOVERED || next == CANCELLED;
            case RECOVERING -> next == RECOVERED;
            case RECOVERED, CANCELLED -> false;
        };
    }
}
