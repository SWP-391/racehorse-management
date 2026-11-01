package vn.racehorse.training.service;

/** Value sets of the training module (DEC-005, DEC-026). */
public final class TrainingEnums {

    private TrainingEnums() {
    }

    /** Nháp, Đang thực hiện, Hoàn thành, Hủy (BR-031). */
    public enum PlanState { DRAFT, ACTIVE, COMPLETED, CANCELLED }

    /** Session states (BR-021, BR-022, BR-038, BR-039, BR-040; DEC-005). */
    public enum SessionState {
        /** Nháp */
        DRAFT,
        /** Đã lên lịch */
        SCHEDULED,
        /** Đang tập */
        IN_PROGRESS,
        /** Hoàn thành */
        COMPLETED,
        /** Hoàn thành (tự động đóng) */
        AUTO_CLOSED,
        /** Bỏ lỡ */
        MISSED,
        /** Bị chặn do khóa */
        BLOCKED_BY_LOCK,
        /** Chờ HLV xem xét */
        AWAITING_REVIEW,
        /** Bỏ lỡ do khóa */
        MISSED_DUE_TO_LOCK,
        /** Hủy */
        CANCELLED;

        /** Not yet run and still part of the plan's future. */
        public boolean isUpcoming() {
            return this == DRAFT || this == SCHEDULED || this == BLOCKED_BY_LOCK || this == AWAITING_REVIEW;
        }
    }

    /** Session type; a trial run (chạy thử) follows every session rule (BR-041). */
    public enum SessionType { TROT, GALLOP, TRIAL_RUN, OTHER }

    /** Intensity rated by the Head Trainer: nhẹ, vừa, nặng (DEC-005). */
    public enum Intensity { LIGHT, MEDIUM, HEAVY }

    /** A Groom's acknowledgement of the session assigned to them: Chưa xác nhận, Đã nhận (BR-118, ST-065). */
    public enum AssignmentAck { UNCONFIRMED, ACKNOWLEDGED }

    /** Where an indicator value came from (DEC-006): never mixed. */
    public enum DataSource { MANUAL, SIMULATED, DEVICE }
}
