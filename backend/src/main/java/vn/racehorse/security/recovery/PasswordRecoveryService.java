package vn.racehorse.security.recovery;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.racehorse.account.EmailAddress;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.mail.RecoveryMailDispatcher;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recovery of a forgotten password by a 6-digit code sent to the email of the account (BR-214 to BR-217, DEC-074).
 * The answer to a request never depends on whether the address has an account: the pace is counted for any address,
 * an unknown address pays the same hashing cost, and the mail goes out after the commit on another thread.
 */
@Service
public class PasswordRecoveryService {

    public static final String SENT_MESSAGE = "Nếu email thuộc một tài khoản đang hoạt động, mã xác nhận đã được gửi.";
    public static final String CODE_REFUSED = "Mã xác nhận không đúng hoặc đã hết hạn.";
    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    public static final Duration MIN_GAP = Duration.ofSeconds(60);
    public static final Duration WINDOW = Duration.ofHours(1);
    public static final int MAX_PER_WINDOW = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository accounts;
    private final PasswordRecoveryRepository recoveries;
    private final PasswordRecoveryRequestRepository requests;
    private final CodeTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final ClubClock clock;
    private final RecoveryMailDispatcher mail;
    /** Compared against when no code can match, so an unknown address costs the same time as a known one. */
    private volatile String dummyHash;

    public PasswordRecoveryService(UserAccountRepository accounts, PasswordRecoveryRepository recoveries,
                                   PasswordRecoveryRequestRepository requests, CodeTokenRepository tokens, PasswordEncoder encoder,
                                   AuditService audit, ClubClock clock, RecoveryMailDispatcher mail) {
        this.accounts = accounts;
        this.recoveries = recoveries;
        this.requests = requests;
        this.tokens = tokens;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
        this.mail = mail;
    }

    /** The answer of a request: never a code. */
    public record Answer(String message, int expiresInSeconds, int resendAfterSeconds) {
    }

    @Transactional
    public Answer request(String emailText) {
        EmailAddress email = EmailAddress.parse(emailText);
        Instant now = clock.now();
        requests.lockAddress(email.value());
        enforcePace(email, now);
        requests.save(new PasswordRecoveryRequest(email.value(), now));

        Optional<UserAccount> account = accounts.findByEmail(email.value()).filter(PasswordRecoveryService::canReceiveCode);
        String code = newCode();
        String hash = encoder.encode(code); // an unknown address hashes too, so the time is the same
        if (account.isPresent()) {
            save(account.get(), hash, now);
            RecoveryPurpose purpose = purposeOf(account.get());
            afterCommit(() -> mail.dispatch(email, code, purpose));
        }
        return new Answer(SENT_MESSAGE, (int) CODE_TTL.toSeconds(), (int) MIN_GAP.toSeconds());
    }

    /** A pending account is invited, not yet usable: it receives an activation code, not a reset code (BR-237). */
    private static boolean canReceiveCode(UserAccount account) {
        return account.isActive() || account.isPending();
    }

    private static RecoveryPurpose purposeOf(UserAccount account) {
        return account.isPending() ? RecoveryPurpose.ACTIVATION : RecoveryPurpose.RESET;
    }

    /** Voids the live code of the account and keeps a new one; the purpose follows the state of the account. */
    private void save(UserAccount account, String hash, Instant now) {
        RecoveryPurpose purpose = purposeOf(account);
        recoveries.voidLive(account.getId());
        recoveries.save(new PasswordRecovery(account.getId(), hash, now, now.plus(purpose.ttl()), purpose));
    }

    /**
     * Mails a new activation code to a pending account: at creation, on a resend by the Club Manager and after the
     * correction of its email (BR-237, BR-238). Every call counts toward the pace of the address; a resend is also held
     * to it. The code never leaves this method except by mail, after the commit.
     */
    @Transactional
    public void issueActivation(UserAccount account, boolean held) {
        EmailAddress email = EmailAddress.parse(account.getEmail());
        Instant now = clock.now();
        requests.lockAddress(email.value());
        if (held) {
            enforcePace(email, now);
        }
        requests.save(new PasswordRecoveryRequest(email.value(), now));
        String code = newCode();
        save(account, encoder.encode(code), now);
        afterCommit(() -> mail.dispatch(email, code, RecoveryPurpose.ACTIVATION));
    }

    /** The account and live code that a correct email and code point to. */
    private record Checked(UserAccount account, PasswordRecovery recovery) {
    }

    /**
     * Checks an email and code the same way for every use: one message for every cause, the wrong try counted, the
     * live code voided at the fifth (BR-215). The code is not used up here.
     */
    private Checked check(String emailText, String code, Instant now) {
        Optional<UserAccount> found = EmailAddress.tryParse(emailText)
                .flatMap(e -> accounts.findByEmail(e.value())).filter(PasswordRecoveryService::canReceiveCode);
        if (found.isEmpty()) {
            encoder.matches(code == null ? "" : code, dummyHash());
            throw codeRefused();
        }
        UserAccount account = accounts.findByIdForUpdate(found.get().getId()).orElseThrow(PasswordRecoveryService::codeRefused);
        Optional<PasswordRecovery> live = recoveries.findLiveForUpdate(account.getId());
        if (live.isEmpty()) {
            encoder.matches(code == null ? "" : code, dummyHash());
            throw codeRefused();
        }
        PasswordRecovery recovery = live.get();
        if (recovery.isExpired(now)) {
            recovery.voidCode();
            recoveries.save(recovery);
            throw codeRefused();
        }
        if (code == null || !encoder.matches(code, recovery.getCodeHash())) {
            recovery.failedTry();
            recoveries.save(recovery);
            throw codeRefused();
        }
        return new Checked(account, recovery);
    }

    /** Uses up the code and saves the password; a pending account becomes active (BR-237, BR-240). */
    private void finish(UserAccount account, PasswordRecovery recovery, String newPassword) {
        if (account.isPending() && account.getRole() == vn.racehorse.account.entity.Role.CLUB_MANAGER) {
            // An invitation never makes a second Club Manager (BR-240, DEC-078); the account stays pending.
            vn.racehorse.account.service.AccountService.requireAssignable(account.getRole());
        }
        recovery.spend();
        recoveries.save(recovery);
        if (account.isPending()) {
            account.activate(encoder.encode(newPassword));
            accounts.save(account);
            audit.recordSystem("ACCOUNT_ACTIVATED", "ACCOUNT", account.getId(), Map.of("email", EmailAddress.mask(account.getEmail())));
            return;
        }
        account.resetPassword(encoder.encode(newPassword));
        accounts.save(account);
        audit.recordSystem("PASSWORD_RESET", "ACCOUNT", account.getId(), Map.of("email", EmailAddress.mask(account.getEmail())));
    }

    /** A refused confirmation still records its wrong try or void, so the exception does not roll back (BR-215). */
    @Transactional(noRollbackFor = ApiException.class)
    public void confirm(String emailText, String code, String newPassword) {
        Checked checked = check(emailText, code, clock.now());
        finish(checked.account(), checked.recovery(), newPassword);
    }

    /**
     * Step 1 of the two-step flow: a correct email and code give a random token valid for 10 minutes and one use.
     * The code stays alive until step 2 saves the password; earlier tokens of the person are ended (BR-255, TECH-F7).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public String verify(String emailText, String code) {
        Instant now = clock.now();
        Checked checked = check(emailText, code, now);
        Long id = checked.account().getId();
        tokens.useUp(id, now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = java.util.HexFormat.of().formatHex(bytes);
        tokens.save(new CodeToken(id, hashToken(token), now.plus(TOKEN_TTL)));
        return token;
    }

    /**
     * Step 2: the token and the new password. The token and the code are used up in this transaction; every failure has
     * the one message of the code (BR-255, TECH-F7). Returns the account so the caller can start its session.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public UserAccount complete(String token, String newPassword) {
        Instant now = clock.now();
        CodeToken stored = tokens.findByTokenHash(hashToken(token == null ? "" : token))
                .filter(t -> t.isUsable(now)).orElseThrow(PasswordRecoveryService::codeRefused);
        UserAccount account = accounts.findByIdForUpdate(stored.getUserId()).filter(PasswordRecoveryService::canReceiveCode)
                .orElseThrow(PasswordRecoveryService::codeRefused);
        // The token (10 minutes) is the proof of step 1; the code itself must still be live and unspent,
        // but its own shorter lifetime is not checked again here (AC-576).
        PasswordRecovery recovery = recoveries.findLiveForUpdate(account.getId())
                .orElseThrow(PasswordRecoveryService::codeRefused);
        finish(account, recovery, newPassword);
        tokens.useUp(account.getId(), now);
        return accounts.findById(account.getId()).orElseThrow();
    }

    private static String hashToken(String token) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Removes the rows that are more than one day old (the pace never looks further back than an hour). */
    @Transactional
    public void cleanup() {
        Instant before = clock.now().minus(Duration.ofDays(1));
        requests.deleteRequestedBefore(before);
        recoveries.deleteCreatedBefore(before);
        tokens.deleteExpiredBefore(before);
    }

    private void enforcePace(EmailAddress email, Instant now) {
        List<Instant> recent = requests.requestTimesSince(email.value(), now.minus(WINDOW));
        if (!recent.isEmpty()) {
            Instant last = recent.get(recent.size() - 1);
            long wait = MIN_GAP.getSeconds() - Duration.between(last, now).getSeconds();
            if (wait > 0) {
                throw tooMany(wait);
            }
        }
        if (recent.size() >= MAX_PER_WINDOW) {
            long wait = Duration.between(now, recent.get(0).plus(WINDOW)).getSeconds() + 1;
            throw tooMany(Math.max(wait, 1));
        }
    }

    static ApiException tooMany(long seconds) {
        String message = "Vui lòng thử lại sau " + (seconds < 120 ? seconds + " giây" : ((seconds + 59) / 60) + " phút") + ".";
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", message,
                Map.of("retryAfterSeconds", String.valueOf(seconds)));
    }

    static ApiException codeRefused() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_CODE", CODE_REFUSED,
                Map.of("code", CODE_REFUSED));
    }

    private static String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = encoder.encode("000000");
            dummyHash = hash;
        }
        return hash;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
