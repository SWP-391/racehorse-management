package vn.racehorse.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import vn.racehorse.account.EmailAddress;
import vn.racehorse.mail.RecoveryMailer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the recovery mails in memory instead of sending them (TECH-226). The only "simulated" sender: the product has
 * none. Tests read the code from here, because the service never returns it.
 */
public class RecordingRecoveryMailer implements RecoveryMailer {

    public record Sent(EmailAddress to, String code) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    @Override
    public void send(EmailAddress to, String code) {
        if (failure != null) {
            throw failure;
        }
        sent.add(new Sent(to, code));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    /** The mails sent to an address, oldest first. */
    public List<Sent> sentTo(String email) {
        return sent.stream().filter(s -> s.to().value().equals(email)).toList();
    }

    public Optional<Sent> lastTo(String email) {
        List<Sent> list = sentTo(email);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public void reset() {
        sent.clear();
        failure = null;
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        RecordingRecoveryMailer recordingRecoveryMailer() {
            return new RecordingRecoveryMailer();
        }
    }
}
