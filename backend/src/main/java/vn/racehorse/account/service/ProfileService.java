package vn.racehorse.account.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.dto.ProfileDto;
import vn.racehorse.account.dto.ProfileRequest;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.util.Map;
import java.util.regex.Pattern;

/** The own profile of every role: name, phone and password; the email is read only (BR-254, BR-212, TECH-F8). */
@Service
public class ProfileService {

    static final int MAX_NAME = AccountService.MAX_NAME;
    static final Pattern PHONE = Pattern.compile("\\+?[0-9][0-9 .()\\-]{6,17}[0-9)]");
    public static final String EMAIL_READ_ONLY = "Email dùng để đăng nhập và không thể tự đổi.";
    public static final String WRONG_OLD_PASSWORD = "Mật khẩu hiện tại không đúng.";

    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AccessGuard access;
    private final AuditService audit;

    public ProfileService(UserAccountRepository accounts, PasswordEncoder encoder, AccessGuard access,
                          AuditService audit) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ProfileDto get() {
        AccountPrincipal me = access.require(Permission.EDIT_OWN_PROFILE);
        return ProfileDto.of(accounts.findById(me.id()).orElseThrow(() -> ApiException.notFound("tài khoản")));
    }

    @Transactional
    public ProfileDto update(ProfileRequest request) {
        AccountPrincipal me = access.require(Permission.EDIT_OWN_PROFILE);
        if (request.email() != null) {
            throw ApiException.invalid("EMAIL_READ_ONLY", "email", EMAIL_READ_ONLY);
        }
        String name = AccountService.tidyName(request.displayName());
        if (name.isEmpty()) {
            throw ApiException.invalid("REQUIRED", "displayName", "Bắt buộc nhập.");
        }
        if (name.length() > MAX_NAME) {
            throw ApiException.invalid("NAME_TOO_LONG", "displayName", "Tên tối đa " + MAX_NAME + " ký tự.");
        }
        String phone = request.phone() == null ? "" : request.phone().strip();
        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
            throw ApiException.invalid("PHONE_INVALID", "phone", "Số điện thoại không hợp lệ.");
        }
        UserAccount account = accounts.findByIdForUpdate(me.id()).orElseThrow(() -> ApiException.notFound("tài khoản"));
        String old = account.getDisplayName();
        if (!old.equals(name)) {
            account.rename(name);
            if (account.getRole() == Role.CLUB_MANAGER) {
                audit.record(me, "ACCOUNT_NAME_CHANGED", "ACCOUNT", account.getId(),
                        Map.of("email", account.emailForAudit(), "oldName", old, "newName", name));
            }
        }
        account.changePhone(phone.isEmpty() ? null : phone);
        return ProfileDto.of(accounts.save(account));
    }

    /**
     * Changes the own password. Every other session of the person ends because the password version moves on; the
     * returned account lets the caller bring the current session to the new version (BR-254).
     */
    @Transactional
    public UserAccount changePassword(String oldPassword, String newPassword) {
        AccountPrincipal me = access.require(Permission.EDIT_OWN_PROFILE);
        UserAccount account = accounts.findByIdForUpdate(me.id()).orElseThrow(() -> ApiException.notFound("tài khoản"));
        if (!encoder.matches(oldPassword, account.getPasswordHash())) {
            throw ApiException.invalid("WRONG_OLD_PASSWORD", "oldPassword", WRONG_OLD_PASSWORD);
        }
        account.resetPassword(encoder.encode(newPassword));
        accounts.save(account);
        audit.record(me, "PASSWORD_CHANGED", "ACCOUNT", account.getId(), Map.of("email", account.emailForAudit()));
        return account;
    }
}
