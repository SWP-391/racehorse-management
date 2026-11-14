package vn.racehorse.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import vn.racehorse.account.EmailAddress;

import java.util.concurrent.RejectedExecutionException;

/**
 * Sends the mail on another thread (so the time of the answer does not depend on the mail service) and swallows every
 * failure into the log (masked address, no code): a failure must never reach the answer of the request, which would
 * tell which addresses have an account (BR-214). {@code app.mail.async=false} sends on the calling thread; tests use
 * it so a mail is there when the request returns.
 */
@Component
public class RecoveryMailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RecoveryMailDispatcher.class);

    private final RecoveryMailer mailer;
    private final TaskExecutor executor;
    private final boolean async;

    public RecoveryMailDispatcher(RecoveryMailer mailer, @Qualifier(MailConfig.EXECUTOR) TaskExecutor executor,
                                  @Value("${app.mail.async:true}") boolean async) {
        this.mailer = mailer;
        this.executor = executor;
        this.async = async;
    }

    public void dispatch(EmailAddress to, String code) {
        if (!async) {
            send(to, code);
            return;
        }
        try {
            executor.execute(() -> send(to, code));
        } catch (RejectedExecutionException e) {
            log.error("recovery mail to {} was not queued: too many mails waiting", to.masked());
        }
    }

    private void send(EmailAddress to, String code) {
        try {
            mailer.send(to, code);
        } catch (RuntimeException e) {
            log.error("recovery mail to {} failed: {}", to.masked(), e.getMessage());
        }
    }
}
