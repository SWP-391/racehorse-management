package vn.racehorse.mail;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.racehorse.account.EmailAddress;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends the recovery mail with the HTTPS API of Brevo, which works on a developer machine and on every Railway plan
 * (Railway blocks outbound SMTP on Free, Trial and Hobby; TECH-226). A reply other than 201 or a timeout is an error;
 * neither the key nor the code is ever logged.
 */
@Component
public class BrevoRecoveryMailer implements RecoveryMailer {

    private static final Logger log = LoggerFactory.getLogger(BrevoRecoveryMailer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final MailProperties properties;
    private final RestClient client;

    @Autowired
    public BrevoRecoveryMailer(MailProperties properties, RestClient.Builder builder) {
        this(properties, builder.requestFactory(timeouts()).build());
    }

    /** For tests that bind a mock server to their own client. */
    public BrevoRecoveryMailer(MailProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        return factory;
    }

    @PostConstruct
    void warnWhenNotConfigured() {
        if (!properties.configured()) {
            log.warn("Recovery mail is not configured: set BREVO_API_KEY and MAIL_FROM. Recovery codes cannot be sent.");
        }
    }

    @Override
    public void send(EmailAddress to, String code) {
        if (!properties.configured()) {
            throw new IllegalStateException("BREVO_API_KEY or MAIL_FROM is not set");
        }
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", properties.fromNameOrDefault(), "email", properties.from()),
                "to", List.of(Map.of("email", to.value())),
                "subject", RecoveryMailText.SUBJECT,
                "htmlContent", RecoveryMailText.html(code),
                "textContent", RecoveryMailText.plain(code));
        var response = client.post()
                .uri(properties.urlOrDefault())
                .header("api-key", properties.brevoApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })
                .toBodilessEntity();
        int status = response.getStatusCode().value();
        if (status != 201) {
            throw new IllegalStateException("Brevo refused the recovery mail to " + to.masked() + ": HTTP " + status);
        }
        log.info("recovery mail sent to {}", to.masked());
    }
}
