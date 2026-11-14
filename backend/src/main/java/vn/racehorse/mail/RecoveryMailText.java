package vn.racehorse.mail;

/** The one mail the application sends: the recovery code, in Vietnamese (BR-217). */
public final class RecoveryMailText {

    public static final String SUBJECT = "Mã xác nhận đặt lại mật khẩu Racehorse";

    private RecoveryMailText() {
    }

    public static String plain(String code) {
        return "Mã xác nhận của bạn là: " + code + "\n\n"
                + "Mã có hiệu lực 5 phút và chỉ dùng được một lần.\n"
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua thư này.";
    }

    public static String html(String code) {
        return "<p>Mã xác nhận của bạn là: <strong style=\"font-size:20px;letter-spacing:2px\">" + code + "</strong></p>"
                + "<p>Mã có hiệu lực 5 phút và chỉ dùng được một lần.</p>"
                + "<p>Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua thư này.</p>";
    }
}
