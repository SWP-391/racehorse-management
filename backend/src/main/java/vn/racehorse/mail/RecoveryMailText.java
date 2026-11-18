package vn.racehorse.mail;

import vn.racehorse.security.recovery.RecoveryPurpose;

/** The mails the application sends: the recovery code and the activation code, in Vietnamese (BR-217, BR-237). */
public final class RecoveryMailText {

    public static final String SUBJECT = "Mã xác nhận đặt lại mật khẩu Racehorse";
    public static final String ACTIVATION_SUBJECT = "Mã kích hoạt tài khoản Racehorse";

    private RecoveryMailText() {
    }

    public static String subject(RecoveryPurpose purpose) {
        return purpose == RecoveryPurpose.ACTIVATION ? ACTIVATION_SUBJECT : SUBJECT;
    }

    public static String plain(String code) {
        return plain(code, RecoveryPurpose.RESET);
    }

    public static String html(String code) {
        return html(code, RecoveryPurpose.RESET);
    }

    public static String plain(String code, RecoveryPurpose purpose) {
        if (purpose == RecoveryPurpose.ACTIVATION) {
            return "Quản lý CLB đã tạo tài khoản Racehorse cho bạn.\n"
                    + "Mã kích hoạt của bạn là: " + code + "\n\n"
                    + "Mở trang Kích hoạt tài khoản, nhập email này, mã trên và mật khẩu bạn chọn.\n"
                    + "Mã có hiệu lực 24 giờ và chỉ dùng được một lần.\n"
                    + "Nếu bạn không biết về tài khoản này, hãy bỏ qua thư này.";
        }
        return "Mã xác nhận của bạn là: " + code + "\n\n"
                + "Mã có hiệu lực 5 phút và chỉ dùng được một lần.\n"
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua thư này.";
    }

    public static String html(String code, RecoveryPurpose purpose) {
        if (purpose == RecoveryPurpose.ACTIVATION) {
            return "<p>Quản lý CLB đã tạo tài khoản Racehorse cho bạn.</p>"
                    + "<p>Mã kích hoạt của bạn là: <strong style=\"font-size:20px;letter-spacing:2px\">" + code + "</strong></p>"
                    + "<p>Mở trang <em>Kích hoạt tài khoản</em>, nhập email này, mã trên và mật khẩu bạn chọn.</p>"
                    + "<p>Mã có hiệu lực 24 giờ và chỉ dùng được một lần.</p>"
                    + "<p>Nếu bạn không biết về tài khoản này, hãy bỏ qua thư này.</p>";
        }
        return "<p>Mã xác nhận của bạn là: <strong style=\"font-size:20px;letter-spacing:2px\">" + code + "</strong></p>"
                + "<p>Mã có hiệu lực 5 phút và chỉ dùng được một lần.</p>"
                + "<p>Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua thư này.</p>";
    }
}
