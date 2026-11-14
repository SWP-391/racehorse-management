package vn.racehorse.mail;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.racehorse.account.EmailAddress;
import org.springframework.core.task.SyncTaskExecutor;
import vn.racehorse.support.LogCapture;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** The recovery mail: its text and the call to the HTTPS API of Brevo (BR-217, TECH-226). */
class BrevoRecoveryMailerTest {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";
    private static final MailProperties PROPERTIES =
            new MailProperties("sender@racehorse.example", "Racehorse", "test-api-key", URL);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BrevoRecoveryMailer mailer = new BrevoRecoveryMailer(PROPERTIES, builder.build());

    @Test
    void theMailStatesTheCodeTheValidityAndTheIgnoreNotice() {
        for (String text : new String[]{RecoveryMailText.plain("123456"), RecoveryMailText.html("123456")}) {
            assertThat(text).contains("123456").contains("5 phút").contains("bỏ qua");
        }
        assertThat(RecoveryMailText.SUBJECT).contains("mật khẩu");
    }

    @Test
    void postsTheMailToBrevoWithTheKeyTheSenderTheRecipientAndTheBody() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(header("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.sender.email").value("sender@racehorse.example"))
                .andExpect(jsonPath("$.sender.name").value("Racehorse"))
                .andExpect(jsonPath("$.to[0].email").value("to@racehorse.example"))
                .andExpect(jsonPath("$.subject").value(RecoveryMailText.SUBJECT))
                .andExpect(jsonPath("$.htmlContent").value(containsString("123456")))
                .andExpect(jsonPath("$.textContent").value(containsString("123456")))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<x@smtp-relay.mailin.fr>\"}"));

        mailer.send(EmailAddress.parse("To@Racehorse.Example"), "123456");

        server.verify();
    }

    @Test
    void aReplyOtherThan201IsAnErrorThatNamesNeitherTheKeyNorTheCode() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"unauthorized\"}"));

        assertThatThrownBy(() -> mailer.send(EmailAddress.parse("to@racehorse.example"), "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("123456")
                .hasMessageNotContaining("test-api-key");
    }

    @Test
    void aTimeoutIsAnErrorThatNamesNeitherTheKeyNorTheCode() {
        server.expect(requestTo(URL)).andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> mailer.send(EmailAddress.parse("to@racehorse.example"), "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageNotContaining("123456")
                .hasMessageNotContaining("test-api-key");
    }

    @Test
    void withoutAKeyOrASenderNothingIsSent() {
        BrevoRecoveryMailer unconfigured = new BrevoRecoveryMailer(
                new MailProperties("", "Racehorse", "", URL), builder.build());

        assertThatThrownBy(() -> unconfigured.send(EmailAddress.parse("to@racehorse.example"), "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BREVO_API_KEY");
        server.verify();
    }

    @Test
    void theDispatcherSwallowsAFailureAndLogsOnlyTheMaskedAddress() {
        AtomicInteger calls = new AtomicInteger();
        RecoveryMailer failing = (to, code) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Brevo refused the recovery mail: HTTP 500");
        };

        String logged;
        try (LogCapture log = new LogCapture()) {
            assertThatCode(() -> new RecoveryMailDispatcher(failing, new SyncTaskExecutor(), false)
                    .dispatch(EmailAddress.parse("tuan@racehorse.example"), "654321")).doesNotThrowAnyException();
            logged = log.all();
        }

        assertThat(calls).hasValue(1);
        assertThat(logged).contains("t***@racehorse.example").doesNotContain("654321")
                .doesNotContain("tuan@racehorse.example");
    }

    @Test
    void aMailerThatThrowsAnIOExceptionStyleErrorIsStillContained() {
        RecoveryMailer failing = (to, code) -> {
            throw new RuntimeException(new IOException("connection reset"));
        };
        assertThatCode(() -> new RecoveryMailDispatcher(failing, new SyncTaskExecutor(), false)
                .dispatch(EmailAddress.parse("tuan@racehorse.example"), "654321")).doesNotThrowAnyException();
    }

    @Test
    void whenAsyncTheMailGoesThroughTheExecutorAndAFullQueueNeverReachesTheCaller() throws Exception {
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        AtomicInteger sent = new AtomicInteger();
        RecoveryMailer ok = (to, code) -> sent.incrementAndGet();
        var queueing = new RecoveryMailDispatcher(ok, queued::add, true);
        queueing.dispatch(EmailAddress.parse("tuan@racehorse.example"), "654321");
        assertThat(sent).hasValue(0);
        assertThat(queued).hasSize(1);
        queued.get(0).run();
        assertThat(sent).hasValue(1);

        var full = new RecoveryMailDispatcher(ok, r -> {
            throw new java.util.concurrent.RejectedExecutionException("queue full");
        }, true);
        assertThatCode(() -> full.dispatch(EmailAddress.parse("tuan@racehorse.example"), "654321"))
                .doesNotThrowAnyException();
    }
}
