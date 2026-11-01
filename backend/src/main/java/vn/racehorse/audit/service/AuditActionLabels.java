package vn.racehorse.audit.service;

import java.util.Map;

/**
 * The Vietnamese name of every audit action code, the same text the audit screen shows (frontend `vi.audit.actions`), so
 * the search can match what the reader sees as well as the stored code (BR-093). A front-end test keeps the two tables equal.
 */
public final class AuditActionLabels {

    private AuditActionLabels() {
    }

    public static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("ACCOUNT_BOOTSTRAPPED", "Khởi tạo tài khoản quản lý"),
        Map.entry("ACCOUNT_CREATED", "Tạo tài khoản"),
        Map.entry("ACCOUNT_DEACTIVATED", "Vô hiệu hóa tài khoản"),
        Map.entry("SESSION_AUTO_CLOSED", "Tự động đóng buổi tập"),
        Map.entry("ROLE_CHANGED", "Đổi vai trò"),
        Map.entry("HORSE_REGISTERED", "Đăng ký ngựa"),
        Map.entry("HORSE_UPDATED", "Sửa hồ sơ ngựa"),
        Map.entry("HORSE_ARCHIVED", "Lưu trữ ngựa"),
        Map.entry("HORSE_RESTORED", "Khôi phục ngựa"),
        Map.entry("HORSE_PHOTO_ADDED", "Thêm ảnh ngựa"),
        Map.entry("HORSE_PHOTO_REPLACED", "Thay ảnh ngựa"),
        Map.entry("HORSE_TRAINER_CHANGED", "Đổi HLV phụ trách ngựa"),
        Map.entry("HORSE_TEMP_TRAINER_SET", "Giao tạm ngựa cho HLV khác"),
        Map.entry("HORSE_TEMP_TRAINER_ENDED", "Hết hạn giao tạm, trả ngựa về HLV chính"),
        Map.entry("RATION_SAVED", "Lưu khẩu phần"),
        Map.entry("SESSION_ASSIGNMENT_DECLINED", "Groom không nhận buổi tập"),
        Map.entry("PEDIGREE_UPDATED", "Cập nhật phả hệ"),
        Map.entry("HEALTH_STATUS_CHANGED", "Đổi tình trạng sức khỏe"),
        Map.entry("LOCK_PLACED", "Khóa ngựa"),
        Map.entry("LOCK_LIFTED", "Mở khóa ngựa"),
        Map.entry("LOCK_REVIEW_REQUESTED", "Yêu cầu xem xét khóa"),
        Map.entry("PENDING_LOCK_REQUESTED", "Yêu cầu khóa chờ duyệt"),
        Map.entry("PENDING_LOCK_WITHDRAWN", "Rút yêu cầu khóa"),
        Map.entry("EXAMINATION_RECORDED", "Ghi nhận khám"),
        Map.entry("THRESHOLD_SET", "Đặt ngưỡng chỉ số"),
        Map.entry("THRESHOLD_CLEARED", "Bỏ ngưỡng chỉ số"),
        Map.entry("TELEMETRY_ALERT", "Cảnh báo chỉ số"),
        Map.entry("CARE_ITEM_CREATED", "Tạo mục chăm sóc"),
        Map.entry("CARE_COMPLETED", "Hoàn thành chăm sóc"),
        Map.entry("CARE_POSTPONED", "Hoãn chăm sóc"),
        Map.entry("CARE_DUE_OVERRIDDEN", "Đổi hạn chăm sóc"),
        Map.entry("INJURY_MARK_CREATED", "Đánh dấu chấn thương"),
        Map.entry("INJURY_MARK_STATUS_CHANGED", "Đổi trạng thái dấu chấn thương"),
        Map.entry("INJURY_MARK_CANCELLED", "Hủy dấu chấn thương"),
        Map.entry("INCIDENT_SENT", "Gửi báo cáo sự cố"),
        Map.entry("INCIDENT_ACKNOWLEDGED", "Xác nhận sự cố"),
        Map.entry("INCIDENT_HANDLED", "Xử lý sự cố"),
        Map.entry("PLAN_CREATED", "Tạo kế hoạch"),
        Map.entry("PLAN_UPDATED", "Sửa kế hoạch"),
        Map.entry("PLAN_ACTIVATED", "Kích hoạt kế hoạch"),
        Map.entry("PLAN_COMPLETED", "Hoàn thành kế hoạch"),
        Map.entry("PLAN_CANCELLED", "Hủy kế hoạch"),
        Map.entry("PLAN_REVIEW_KEPT", "Giữ nguyên sau xem xét kế hoạch"),
        Map.entry("PLAN_SHIFTED", "Dời kế hoạch"),
        Map.entry("PLAN_VERSION_RESTORED", "Khôi phục phiên bản kế hoạch"),
        Map.entry("SESSION_DRAFTED", "Lưu nháp buổi tập"),
        Map.entry("SESSION_SCHEDULED", "Lên lịch buổi tập"),
        Map.entry("SESSION_RESPONSIBLE_CHANGED", "Đổi người phụ trách buổi tập"),
        Map.entry("SESSION_ASSIGNMENT_ACKNOWLEDGED", "Xác nhận phân công buổi tập"),
        Map.entry("SESSION_CANCELLED", "Hủy buổi tập"),
        Map.entry("SESSION_STARTED", "Bắt đầu buổi tập"),
        Map.entry("SESSION_COMPLETED", "Kết thúc buổi tập"),
        Map.entry("SESSION_INDICATORS_COMPLETED", "Hoàn tất chỉ số buổi tập"),
        Map.entry("TRIAL_VIDEO_UPLOADED", "Tải video chạy thử"),
        Map.entry("TRIAL_VIDEO_DELETED", "Xóa video chạy thử"),
        Map.entry("SETTING_CHANGED", "Đổi cài đặt"),
        Map.entry("CHORE_MARKED", "Đánh dấu việc chăm sóc"),
        Map.entry("CHORE_CANCELLED", "Hủy đánh dấu việc chăm sóc"),
        Map.entry("STALL_CHANGED", "Đổi ô chuồng"),
        Map.entry("AREA_CREATED", "Thêm khu chuồng"),
        Map.entry("AREA_RENAMED", "Đổi tên khu chuồng"),
        Map.entry("AREA_RESIZED", "Đổi sức chứa khu chuồng"),
        Map.entry("AREA_DELETED", "Xóa khu chuồng"),
        Map.entry("SCHEDULE_CHANGED", "Đổi giờ làm chuồng của Groom"),
        Map.entry("SCHEDULE_CLEARED", "Xóa giờ làm chuồng của Groom"),
        Map.entry("LEAVE_RECORDED", "Ghi nghỉ phép"),
        Map.entry("LEAVE_CANCELLED", "Hủy ngày nghỉ phép"),
        Map.entry("SUPPLY_CREATED", "Thêm vật tư"),
        Map.entry("SUPPLY_UPDATED", "Sửa vật tư"),
        Map.entry("SUPPLY_PRICE_SET", "Đặt giá vật tư"),
        Map.entry("STOCK_RECEIVED", "Nhập kho"),
        Map.entry("STOCK_ADJUSTED", "Điều chỉnh kho"),
        Map.entry("EXPECTED_RECEIPT_ADDED", "Thêm lô dự kiến nhập"),
        Map.entry("EXPECTED_RECEIPT_CANCELLED", "Hủy lô dự kiến nhập"),
        Map.entry("CARE_ITEM_PLAN_SET", "Đặt thuốc và liều dự kiến"),
        Map.entry("CHORE_BACKFILLED", "Bổ sung việc của ngày đã qua"),
        Map.entry("BILLING_PRICE_SET", "Đặt giá dịch vụ"),
        Map.entry("FEE_TYPE_CREATED", "Thêm loại phí khám"),
        Map.entry("FEE_TYPE_UPDATED", "Sửa loại phí khám"),
        Map.entry("BILLING_MONTH_CLOSED", "Chốt tháng"),
        Map.entry("BILLING_ADJUSTMENT_ADDED", "Thêm khoản điều chỉnh"),
        Map.entry("RESTOCK_PROPOSED", "Đề xuất bổ sung vật tư"),
        Map.entry("RESTOCK_APPROVED", "Duyệt đề xuất bổ sung"),
        Map.entry("RESTOCK_REJECTED", "Từ chối đề xuất bổ sung"));

    /** The Vietnamese name of every audit target type (frontend `vi.audit.targets`). */
    public static final Map<String, String> TARGETS = Map.ofEntries(
        Map.entry("HORSE", "Ngựa"),
        Map.entry("SESSION", "Buổi tập"),
        Map.entry("PLAN", "Kế hoạch"),
        Map.entry("ACCOUNT", "Tài khoản"),
        Map.entry("INCIDENT", "Sự cố"),
        Map.entry("CARE_ITEM", "Mục chăm sóc"),
        Map.entry("SETTING", "Cài đặt"),
        Map.entry("CHORE_ENTRY", "Việc chăm sóc"),
        Map.entry("AREA", "Khu chuồng"),
        Map.entry("SUPPLY_ITEM", "Vật tư"),
        Map.entry("RESTOCK_PROPOSAL", "Đề xuất bổ sung"),
        Map.entry("SERVICE_PRICE", "Bảng giá dịch vụ"),
        Map.entry("EXAM_FEE_TYPE", "Loại phí khám"),
        Map.entry("BILLING_MONTH", "Chi phí chủ ngựa"));

    /** The label of an action, or the code itself when it has none. */
    public static String of(String action) {
        return LABELS.getOrDefault(action, action);
    }

    /** The label of a target type, or the type itself when it has none. */
    public static String target(String targetType) {
        return TARGETS.getOrDefault(targetType, targetType);
    }
}
