package vn.racehorse.health.entity;

/** The Veterinarian's choice when lifting a lock (BR-022, DEC-003); slice 3 applies it to blocked sessions. */
public enum ReturnLevel {
    /** Cho phép tiếp tục nguyên lịch */
    RESUME_AS_IS,
    /** Cho phép tập có điều chỉnh */
    RESUME_WITH_ADJUSTMENT
}
