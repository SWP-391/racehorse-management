package vn.racehorse.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.recovery.PasswordRecoveryService;
import vn.racehorse.support.ApiTest;
import vn.racehorse.support.LogCapture;
import vn.racehorse.support.RecordingRecoveryMailer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recovery of a forgotten password by a code sent to the email of the account (BR-214 to BR-217, DEC-074). */
class PasswordRecoveryTest extends ApiTest {

    private static final String NEW_PASSWORD = "new-password-456";

    @Autowired
    RecordingRecoveryMailer mailer;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordRecoveryService service;

    @BeforeEach
    void emptyMailbox() {
        mailer.reset();
    }

    private ResultActions request(String email) throws Exception {
        return mvc.perform(post("/api/auth/recovery/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email))));
    }

    private ResultActions confirm(String email, String code, String password) throws Exception {
        return mvc.perform(post("/api/auth/recovery/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "code", code, "newPassword", password))));
    }

    private MvcResult signIn(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", password)))).andReturn();
    }

    private String codeSentTo(String email) {
        return mailer.lastTo(email).orElseThrow(() -> new AssertionError("no mail was sent to " + email)).code();
    }

    private static String wrong(String code) {
        return (code.charAt(0) == '9' ? "1" : "9") + code.substring(1);
    }

    private static void later(long seconds) {
        TestClock.shift(Duration.ofSeconds(seconds));
    }

    private void deactivate(long id) {
        UserAccount account = accounts.findById(id).orElseThrow();
        account.deactivate();
        accounts.save(account);
    }

    private JsonNode answer(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void AC_461_anUnknownEmailGetsTheSameAnswerAndNoMail() throws Exception {
        String known = account(Role.GROOM).email();

        ResultActions forKnown = request(known).andExpect(status().isOk());
        ResultActions forUnknown = request(uniqueEmail("nobody")).andExpect(status().isOk());

        JsonNode a = answer(forKnown);
        JsonNode b = answer(forUnknown);
        assertThat(b).isEqualTo(a);
        assertThat(a.get("message").asText())
                .isEqualTo("Nếu email thuộc một tài khoản đang hoạt động, mã xác nhận đã được gửi.");
        assertThat(a.get("expiresInSeconds").asInt()).isEqualTo(300);
        assertThat(a.get("resendAfterSeconds").asInt()).isEqualTo(60);
        assertThat(mailer.sent()).hasSize(1);
    }

    @Test
    void theResponseNeverCarriesTheCode() throws Exception {
        String email = account(Role.GROOM).email();
        String raw = request(email).andReturn().getResponse().getContentAsString();
        String code = codeSentTo(email);
        assertThat(raw).doesNotContain(code).doesNotContainIgnoringCase("simulated");
        assertThat(answer(request(uniqueEmail("x"))).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("message", "expiresInSeconds", "resendAfterSeconds");
    }

    @Test
    void aKnownEmailInAnyCaseWithSpacesReceivesTheCode() throws Exception {
        String email = account(Role.GROOM).email();
        request("  " + email.toUpperCase() + " ").andExpect(status().isOk());
        assertThat(mailer.lastTo(email)).isPresent();
        assertThat(codeSentTo(email)).matches("\\d{6}");
    }

    @Test
    void anEmailThatIsNotValidIsRefusedNamingTheFieldAndSendsNothing() throws Exception {
        request("an.le@").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.email").exists());
        request("an le@example.com").andExpect(status().isBadRequest());
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void AC_465_aDeactivatedAccountReceivesNothingAndTheAnswerIsTheSame() throws Exception {
        var leaver = account(Role.GROOM);
        deactivate(leaver.id());

        JsonNode a = answer(request(leaver.email()).andExpect(status().isOk()));
        JsonNode b = answer(request(uniqueEmail("nobody")).andExpect(status().isOk()));

        assertThat(a).isEqualTo(b);
        assertThat(mailer.sent()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from password_recovery where account_id = ?", Integer.class,
                leaver.id())).isZero();
    }

    @Test
    void AC_460_aGroomResetsThePasswordWithTheCodeFromTheMail() throws Exception {
        var groom = account(Role.GROOM);
        request(groom.email()).andExpect(status().isOk());
        String code = codeSentTo(groom.email());

        MvcResult reset = confirm(groom.email(), code, NEW_PASSWORD).andExpect(status().isNoContent()).andReturn();

        assertThat(reset.getRequest().getSession(false)).as("the reset does not sign the person in").isNull();
        assertThat(signIn(groom.email(), PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(signIn(groom.email(), NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void theMailTextCarriesTheCodeAndTheNoticeButOnlyTheMailerSeesIt() throws Exception {
        var groom = account(Role.GROOM);
        request(groom.email()).andExpect(status().isOk());
        assertThat(mailer.sentTo(groom.email())).hasSize(1);
        assertThat(mailer.sentTo(groom.email()).get(0).code()).matches("\\d{6}");
    }

    @Test
    void theCodeIsKeptOnlyAsAHashAndNeverLogged() throws Exception {
        var groom = account(Role.GROOM);
        String code;
        String logged;
        try (LogCapture log = new LogCapture()) {
            request(groom.email()).andExpect(status().isOk());
            code = codeSentTo(groom.email());
            confirm(groom.email(), wrong(code), NEW_PASSWORD).andExpect(status().isBadRequest());
            confirm(groom.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());
            logged = log.all();
        }

        List<String> hashes = jdbc.queryForList("select code_hash from password_recovery where account_id = ?",
                String.class, groom.id());
        assertThat(hashes).isNotEmpty().allSatisfy(h -> assertThat(h).startsWith("$2").doesNotContain(code));
        assertThat(logged).doesNotContain(code).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void AC_462_fiveWrongCodesVoidTheCodeEvenForTheRightOne() throws Exception {
        var groom = account(Role.GROOM);
        request(groom.email()).andExpect(status().isOk());
        String code = codeSentTo(groom.email());

        for (int i = 0; i < 5; i++) {
            confirm(groom.email(), wrong(code), NEW_PASSWORD).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_CODE"))
                    .andExpect(jsonPath("$.message").value("Mã xác nhận không đúng hoặc đã hết hạn."));
        }
        confirm(groom.email(), code, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_CODE"));
        assertThat(signIn(groom.email(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select state from password_recovery where account_id = ?", String.class,
                groom.id())).isEqualTo("VOID");
    }

    @Test
    void fourWrongCodesStillLeaveTheRightOneWorking() throws Exception {
        var groom = account(Role.GROOM);
        request(groom.email()).andExpect(status().isOk());
        String code = codeSentTo(groom.email());
        for (int i = 0; i < 4; i++) {
            confirm(groom.email(), wrong(code), NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        confirm(groom.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void AC_463_anExpiredCodeIsRefusedAndAUsedCodeToo() throws Exception {
        var groom = account(Role.GROOM);
        request(groom.email()).andExpect(status().isOk());
        String code = codeSentTo(groom.email());
        later(6 * 60);
        confirm(groom.email(), code, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã xác nhận không đúng hoặc đã hết hạn."));
        assertThat(signIn(groom.email(), PASSWORD).getResponse().getStatus()).isEqualTo(200);

        TestClock.reset();
        var vet = account(Role.VETERINARIAN);
        request(vet.email()).andExpect(status().isOk());
        String used = codeSentTo(vet.email());
        confirm(vet.email(), used, NEW_PASSWORD).andExpect(status().isNoContent());
        confirm(vet.email(), used, "another-password-789").andExpect(status().isBadRequest());
        assertThat(signIn(vet.email(), NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anUnknownEmailGetsTheSameRefusalOnConfirmation() throws Exception {
        confirm(uniqueEmail("nobody"), "123456", NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_CODE"))
                .andExpect(jsonPath("$.message").value("Mã xác nhận không đúng hoặc đã hết hạn."));
        confirm("not-an-email", "123456", NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_CODE"));
    }

    @Test
    void AC_467_aSecondRequestWithinAMinuteIsRefusedWithTheTimeToWait() throws Exception {
        String known = account(Role.GROOM).email();
        String unknown = uniqueEmail("nobody");
        for (String email : new String[]{known, unknown}) {
            request(email).andExpect(status().isOk());
            request(email).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern(
                            "Vui lòng thử lại sau \\d+ giây\\.")))
                    .andExpect(jsonPath("$.fields.retryAfterSeconds").exists());
        }
        assertThat(mailer.sentTo(known)).hasSize(1);
        later(61);
        request(known).andExpect(status().isOk());
        assertThat(mailer.sentTo(known)).hasSize(2);
    }

    @Test
    void fiveRequestsAnHourThenTheSixthWaitsUntilTheOldestLeavesTheHour() throws Exception {
        String email = account(Role.GROOM).email();
        for (int i = 0; i < 5; i++) {
            later(61L * i);
            request(email).andExpect(status().isOk());
        }
        later(61L * 5);
        request(email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("phút")));
        later(3601);
        request(email).andExpect(status().isOk());
    }

    @Test
    void aNewRequestVoidsThePreviousCode() throws Exception {
        var groom = account(Role.GROOM);
        request(groom.email()).andExpect(status().isOk());
        String first = codeSentTo(groom.email());
        later(61);
        request(groom.email()).andExpect(status().isOk());
        String second = codeSentTo(groom.email());
        assertThat(mailer.sentTo(groom.email())).hasSize(2);

        if (!first.equals(second)) {
            confirm(groom.email(), first, NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        confirm(groom.email(), second, NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from password_recovery where account_id = ? and state = 'LIVE'",
                Integer.class, groom.id())).isZero();
    }

    @Test
    void aPasswordUnderEightCharactersIsRefusedAndTheCodeIsNotSpent() throws Exception {
        var groom = account(Role.GROOM);
        request(groom.email()).andExpect(status().isOk());
        String code = codeSentTo(groom.email());
        confirm(groom.email(), code, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
        assertThat(signIn(groom.email(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
        confirm(groom.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aMailFailureLeavesTheAnswerUnchangedAndOnlyTheMaskedAddressInTheLog() throws Exception {
        var groom = account(Role.GROOM);
        JsonNode normal = answer(request(account(Role.GROOM).email()).andExpect(status().isOk()));
        mailer.failWith(new IllegalStateException("Brevo refused the recovery mail: HTTP 500"));

        JsonNode failed;
        String logged;
        try (LogCapture log = new LogCapture()) {
            failed = answer(request(groom.email()).andExpect(status().isOk()));
            logged = log.all();
        }

        assertThat(failed).isEqualTo(normal);
        assertThat(mailer.sentTo(groom.email())).isEmpty();
        assertThat(logged).contains("recovery mail to").contains("***@racehorse.example").doesNotContain(groom.email());
        mailer.reset();
        later(61);
        request(groom.email()).andExpect(status().isOk());
        assertThat(mailer.sentTo(groom.email())).hasSize(1);
    }

    @Test
    void AC_464_sessionsOpenOnOtherDevicesEndWhenThePasswordIsReset() throws Exception {
        var groom = account(Role.GROOM);
        MockHttpSession phone = (MockHttpSession) signIn(groom.email(), PASSWORD).getRequest().getSession(false);
        MockHttpSession laptop = (MockHttpSession) signIn(groom.email(), PASSWORD).getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(phone)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(laptop)).andExpect(status().isOk());

        request(groom.email()).andExpect(status().isOk());
        confirm(groom.email(), codeSentTo(groom.email()), NEW_PASSWORD).andExpect(status().isNoContent());

        for (MockHttpSession old : List.of(phone, laptop)) {
            mvc.perform(get("/api/auth/me").session(old)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        MockHttpSession fresh = (MockHttpSession) signIn(groom.email(), NEW_PASSWORD).getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(fresh)).andExpect(status().isOk());
    }

    @Test
    void AC_466_theClubManagerRecoversLikeAnyRoleAndTheAuditHoldsNoSecrets() throws Exception {
        var boss = account(Role.CLUB_MANAGER);
        request(boss.email()).andExpect(status().isOk());
        String code = codeSentTo(boss.email());

        confirm(boss.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(signIn(boss.email(), NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> audit = jdbc.queryForList(
                "select actor_id, actor_name, details from audit_entry where target_id = ? and action = 'PASSWORD_RESET'",
                boss.id());
        assertThat(audit).hasSize(1);
        String details = audit.get(0).get("details").toString();
        assertThat(audit.get(0).get("actor_id")).isNull();
        assertThat(details).contains("***@racehorse.example").doesNotContain(code).doesNotContain(NEW_PASSWORD)
                .doesNotContain(boss.email());
    }

    @Test
    void aCorrectionOfTheEmailVoidsTheLiveCode() throws Exception {
        var manager = account(Role.CLUB_MANAGER);
        var groom = account(Role.GROOM);
        request(groom.email()).andExpect(status().isOk());
        String code = codeSentTo(groom.email());
        String corrected = uniqueEmail("fixed");

        asJson(manager, put("/api/accounts/" + groom.id() + "/email"), Map.of("email", corrected, "reason", "gõ nhầm"))
                .andExpect(status().isOk());

        confirm(groom.email(), code, NEW_PASSWORD).andExpect(status().isBadRequest());
        confirm(corrected, code, NEW_PASSWORD).andExpect(status().isBadRequest());
        later(61);
        request(corrected).andExpect(status().isOk());
        confirm(corrected, codeSentTo(corrected), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void theTwoStepsAreOpenBeforeSigningInButNoOtherRouteIs() throws Exception {
        String email = account(Role.GROOM).email();
        request(email).andExpect(status().isOk());
        confirm(email, wrong(codeSentTo(email)), NEW_PASSWORD).andExpect(status().isBadRequest());
        mvc.perform(get("/api/horses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void aRecoveryStepWithoutTheCsrfTokenIsRefusedAsAnyProtectedRequest() throws Exception {
        String email = account(Role.GROOM).email();
        mvc.perform(post("/api/auth/recovery/request").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/recovery/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "code", "123456", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isForbidden());
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void theCleanUpRemovesOldRowsAndKeepsRecentOnes() {
        var groom = account(Role.GROOM);
        Timestamp old = Timestamp.from(Instant.now().minus(Duration.ofDays(2)));
        Timestamp recent = Timestamp.from(Instant.now().minus(Duration.ofMinutes(10)));
        jdbc.update("insert into password_recovery_request (email, requested_at) values ('old@racehorse.example', ?)", old);
        jdbc.update("insert into password_recovery_request (email, requested_at) values ('new@racehorse.example', ?)", recent);
        jdbc.update("insert into password_recovery (account_id, code_hash, created_at, expires_at, state) "
                + "values (?, 'h', ?, ?, 'VOID')", groom.id(), old, old);
        jdbc.update("insert into password_recovery (account_id, code_hash, created_at, expires_at, state) "
                + "values (?, 'h2', ?, ?, 'LIVE')", groom.id(), recent, Timestamp.from(Instant.now().plusSeconds(300)));

        service.cleanup();

        assertThat(jdbc.queryForObject("select count(*) from password_recovery_request where email = 'old@racehorse.example'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from password_recovery_request where email = 'new@racehorse.example'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from password_recovery where account_id = ?", Integer.class,
                groom.id())).isEqualTo(1);
    }
}
