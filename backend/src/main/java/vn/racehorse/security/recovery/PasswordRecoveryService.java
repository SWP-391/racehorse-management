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
    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    public static final Duration MIN_GAP = Duration.ofSeconds(60);
    public static final Duration WINDOW = Duration.ofHours(1);
    public static final int MAX_PER_WINDOW = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository accounts;
    private final PasswordRecoveryRepository recoveries;
    private final PasswordRecoveryRequestRepository requests;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final ClubClock clock;
    private final RecoveryMailDispatcher mail;
    /** Compared against when no code can match, so an unknown address costs the same time as a known one. */
    private volatile String dummyHash;

    public PasswordRecoveryService(UserAccountRepository accounts, PasswordRecoveryRepository recoveries,
                                   PasswordRecoveryRequestRepository requests, PasswordEncoder encoder,
                                   AuditService audit, ClubClock clock, RecoveryMailDispatcher mail) {
        this.accounts = accounts;
        this.recoveries = recoveries;
        this.requests = requests;
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

        Optional<UserAccount> account = accounts.findByEmail(email.value()).filter(UserAccount::isActive);
        String code = newCode();
        String hash = encoder.encode(code); // an unknown address hashes too, so the time is the same
        if (account.isPresent()) {
            Long id = account.get().getId();
            recoveries.voidLive(id);
            recoveries.save(new PasswordRecovery(id, hash, now, now.plus(CODE_TTL)));
            afterCommit(() -> mail.dispatch(email, code));
        }
        return new Answer(SENT_MESSAGE, (int) CODE_TTL.toSeconds(), (int) MIN_GAP.toSeconds());
    }

    /** A refused confirmation still records its wrong try or void, so the exception does not roll back (BR-215). */
    @Transactional(noRollbackFor = ApiException.class)
    public void confirm(String emailText, String code, String newPassword) {
        Instant now = clock.now();
        Optional<UserAccount> found = EmailAddress.tryParse(emailText)
                .flatMap(e -> accounts.findByEmail(e.value())).filter(UserAccount::isActive);
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
        recovery.spend();
        recoveries.save(recovery);
        account.resetPassword(encoder.encode(newPassword));
        accounts.save(account);
        audit.recordSystem("PASSWORD_RESET", "ACCOUNT", account.getId(), Map.of("email", EmailAddress.mask(account.getEmail())));
    }

    /** Removes the rows that are more than one day old (the pace never looks further back than an hour). */
    @Transactional
    public void cleanup() {
        Instant before = clock.now().minus(Duration.ofDays(1));
        requests.deleteRequestedBefore(before);
        recoveries.deleteCreatedBefore(before);
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
