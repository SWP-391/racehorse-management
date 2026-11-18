package vn.racehorse.account;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.recovery.RecoveryPurpose;
import vn.racehorse.support.ApiTest;
import vn.racehorse.support.RecordingRecoveryMailer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An account created without a password is pending and its owner activates it with a mailed code (BR-235 to BR-238, DEC-076). */
class AccountActivationTest extends ApiTest {

    private static final String CHOSEN = "chosen-password-789";

    @Autowired
    RecordingRecoveryMailer mailer;
    @Autowired
    JdbcTemplate jdbc;

    AccountPrincipal manager;

    @BeforeEach
    void setUp() {
        mailer.reset();
        manager = account(Role.CLUB_MANAGER);
    }

    private Map<String, Object> invite(String email, Role role) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("displayName", "Người mới");
        body.put("roles", List.of(role.name()));
        return body;
    }

    private JsonNode created(String email, Role role) throws Exception {
        return body(asJson(manager, post("/api/accounts"), invite(email, role)).andExpect(status().isCreated()));
    }

    private String codeOf(String email) {
        return mailer.lastTo(email).orElseThrow(() -> new AssertionError("no mail to " + email)).code();
    }

    private static String wrong(String code) {
        return (code.charAt(0) == '9' ? "1" : "9") + code.substring(1);
    }

    private ResultActions activate(String email, String code, String password) throws Exception {
        return mvc.perform(post("/api/auth/recovery/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "code", code, "newPassword", password))));
    }

    private ResultActions requestCode(String email) throws Exception {
        return mvc.perform(post("/api/auth/recovery/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email))));
    }

    private MvcResult signIn(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", password)))).andReturn();
    }

    private ResultActions resend(AccountPrincipal who, long id) throws Exception {
        return as(who, post("/api/accounts/" + id + "/activation-code"));
    }

    private UserAccount stored(String email) {
        return accounts.findByEmail(email).orElseThrow();
    }

    @Test
    void AC_505_anInviteIsPendingAndMailedACodeThatTheAnswerNeverShows() throws Exception {
        String email = uniqueEmail("moi");

        ResultActions result = asJson(manager, post("/api/accounts"), invite(email, Role.VETERINARIAN))
                .andExpect(status().isCreated());
        String raw = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode dto = json.readTree(raw);
        assertThat(dto.get("pending").asBoolean()).isTrue();
        assertThat(dto.get("active").asBoolean()).isFalse();
        assertThat(mailer.sentTo(email)).hasSize(1);
        assertThat(mailer.lastTo(email).orElseThrow().purpose()).isEqualTo(RecoveryPurpose.ACTIVATION);
        assertThat(codeOf(email)).matches("\\d{6}");
        assertThat(raw).doesNotContain(codeOf(email));
        assertThat(jdbc.queryForList("select details from audit_entry where target_id = ? and action = 'ACCOUNT_CREATED'",
                dto.get("id").asLong()).get(0).get("details").toString()).contains("INVITE");
    }

    @Test
    void theActivationCodeLivesTwentyFourHoursAndIsKeptOnlyAsAHash() throws Exception {
        String email = uniqueEmail("moi");
        long id = created(email, Role.GROOM).get("id").asLong();

        Map<String, Object> row = jdbc.queryForMap(
                "select purpose, code_hash, extract(epoch from (expires_at - created_at)) as seconds from password_recovery where account_id = ?", id);

        assertThat(row.get("purpose")).isEqualTo("ACTIVATION");
        assertThat(((Number) row.get("seconds")).longValue()).isEqualTo(Duration.ofHours(24).toSeconds());
        assertThat((String) row.get("code_hash")).isNotEqualTo(codeOf(email)).startsWith("$2");
    }

    @Test
    void anAccountCreatedWithAPasswordStaysActiveAndGetsNoMail() throws Exception {
        String email = uniqueEmail("ngay");
        Map<String, Object> body = invite(email, Role.GROOM);
        body.put("password", CHOSEN);

        JsonNode dto = body(asJson(manager, post("/api/accounts"), body).andExpect(status().isCreated()));

        assertThat(dto.get("pending").asBoolean()).isFalse();
        assertThat(dto.get("active").asBoolean()).isTrue();
        assertThat(mailer.sent()).isEmpty();
        assertThat(signIn(email, CHOSEN).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void AC_517_aShortPasswordIsStillRefusedNamingTheField() throws Exception {
        String email = uniqueEmail("ngan");
        Map<String, Object> body = invite(email, Role.GROOM);
        body.put("password", "short");

        asJson(manager, post("/api/accounts"), body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());

        assertThat(accounts.existsByEmail(email)).isFalse();
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void aBlankPasswordMeansAnInvite() throws Exception {
        Map<String, Object> body = invite(uniqueEmail("trong"), Role.GROOM);
        body.put("password", "   ");

        assertThat(body(asJson(manager, post("/api/accounts"), body).andExpect(status().isCreated()))
                .get("pending").asBoolean()).isTrue();
    }

    @Test
    void aDuplicateEmailIsRefusedInTheInviteModeToo() throws Exception {
        AccountPrincipal existing = account(Role.GROOM);

        asJson(manager, post("/api/accounts"), invite(existing.email(), Role.GROOM))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void AC_506_aFailingMailerStillLeavesAPendingAccount() throws Exception {
        mailer.failWith(new IllegalStateException("Brevo refused the recovery mail: HTTP 500"));
        String email = uniqueEmail("loi");

        JsonNode dto = created(email, Role.GROOM);

        assertThat(dto.get("pending").asBoolean()).isTrue();
        assertThat(stored(email).isPending()).isTrue();
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void AC_507_aPendingAccountCannotSignIn() throws Exception {
        String email = uniqueEmail("cho");
        created(email, Role.GROOM);
        MvcResult wrongPassword = signIn(account(Role.GROOM).email(), "wrong-password");

        MvcResult pending = signIn(email, CHOSEN);

        assertThat(pending.getResponse().getStatus()).isEqualTo(401);
        assertThat(pending.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(wrongPassword.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void AC_508_aPendingGroomIsNotOfferedForAssignment() throws Exception {
        String email = uniqueEmail("groom");
        long id = created(email, Role.GROOM).get("id").asLong();
        long active = account(Role.GROOM).id();

        JsonNode options = body(as(account(Role.HEAD_TRAINER), get("/api/options/grooms")).andExpect(status().isOk()));

        List<Long> ids = new java.util.ArrayList<>();
        options.forEach(o -> ids.add(o.get("id").asLong()));
        assertThat(ids).contains(active).doesNotContain(id);
    }

    @Test
    void AC_509_aRightCodeActivatesTheAccountAndItsPersonSignsIn() throws Exception {
        String email = uniqueEmail("kichhoat");
        long id = created(email, Role.VETERINARIAN).get("id").asLong();
        String code = codeOf(email);

        activate(email, code, CHOSEN).andExpect(status().isNoContent());

        UserAccount account = stored(email);
        assertThat(account.isActive()).isTrue();
        assertThat(account.isPending()).isFalse();
        assertThat(signIn(email, CHOSEN).getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> audit = jdbc.queryForList(
                "select details from audit_entry where target_id = ? and action = 'ACCOUNT_ACTIVATED'", id);
        assertThat(audit).hasSize(1);
        String details = audit.get(0).get("details").toString();
        assertThat(details).contains("***@").doesNotContain(code).doesNotContain(CHOSEN).doesNotContain(email);
    }

    @Test
    void anActivatedAccountIsNotPendingAgainAndTheCodeIsSpent() throws Exception {
        String email = uniqueEmail("mot-lan");
        created(email, Role.GROOM);
        String code = codeOf(email);
        activate(email, code, CHOSEN).andExpect(status().isNoContent());

        activate(email, code, "another-password-1").andExpect(status().isBadRequest());

        assertThat(signIn(email, CHOSEN).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void AC_510_aWrongExpiredOrSpentCodeIsRefusedWithOneAnswer() throws Exception {
        String email = uniqueEmail("sai");
        created(email, Role.GROOM);
        String code = codeOf(email);

        String wrongAnswer = activate(email, wrong(code), CHOSEN).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknownAnswer = activate(uniqueEmail("khong"), code, CHOSEN).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(wrongAnswer).isEqualTo(unknownAnswer).contains("INVALID_OR_EXPIRED_CODE");

        TestClock.shift(Duration.ofHours(24).plusSeconds(1));
        String expired = activate(email, code, CHOSEN).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(expired).isEqualTo(wrongAnswer);
        assertThat(stored(email).isPending()).isTrue();
    }

    @Test
    void theCodeStillWorksJustBeforeTwentyFourHours() throws Exception {
        String email = uniqueEmail("kip");
        created(email, Role.GROOM);
        String code = codeOf(email);

        TestClock.shift(Duration.ofHours(23).plusMinutes(59));

        activate(email, code, CHOSEN).andExpect(status().isNoContent());
    }

    @Test
    void AC_511_fiveWrongTriesVoidTheCode() throws Exception {
        String email = uniqueEmail("nam");
        created(email, Role.GROOM);
        String code = codeOf(email);

        for (int i = 0; i < 5; i++) {
            activate(email, wrong(code), CHOSEN).andExpect(status().isBadRequest());
        }
        activate(email, code, CHOSEN).andExpect(status().isBadRequest());

        assertThat(stored(email).isPending()).isTrue();
    }

    @Test
    void aShortNewPasswordIsRefusedWithoutSpendingTheCode() throws Exception {
        String email = uniqueEmail("ngan");
        created(email, Role.GROOM);
        String code = codeOf(email);

        activate(email, code, "short").andExpect(status().isBadRequest());
        activate(email, code, CHOSEN).andExpect(status().isNoContent());
    }

    @Test
    void AC_512_aResendReplacesTheCodeAndIsPaced() throws Exception {
        String email = uniqueEmail("gui-lai");
        long id = created(email, Role.GROOM).get("id").asLong();
        String first = codeOf(email);

        resend(manager, id).andExpect(status().isTooManyRequests());
        TestClock.shift(Duration.ofSeconds(61));
        resend(manager, id).andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(true));

        assertThat(mailer.sentTo(email)).hasSize(2);
        String second = codeOf(email);
        assertThat(second).isNotEqualTo(first);
        activate(email, first, CHOSEN).andExpect(status().isBadRequest());
        resend(manager, id).andExpect(status().isTooManyRequests());
        activate(email, second, CHOSEN).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("select details from audit_entry where target_id = ? and action = 'ACTIVATION_CODE_SENT'", id))
                .hasSize(1);
    }

    @Test
    void onlyTheClubManagerResends() throws Exception {
        String email = uniqueEmail("quyen");
        long id = created(email, Role.GROOM).get("id").asLong();

        resend(account(Role.HEAD_TRAINER), id).andExpect(status().isForbidden());
    }

    @Test
    void AC_513_aCorrectedEmailVoidsTheOldCodeAndMailsTheNewAddress() throws Exception {
        String wrongEmail = uniqueEmail("nham");
        String rightEmail = uniqueEmail("dung");
        long id = created(wrongEmail, Role.GROOM).get("id").asLong();
        String oldCode = codeOf(wrongEmail);

        asJson(manager, put("/api/accounts/" + id + "/email"), Map.of("email", rightEmail, "reason", "Gõ nhầm email"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(true));

        assertThat(mailer.sentTo(rightEmail)).hasSize(1);
        assertThat(mailer.lastTo(rightEmail).orElseThrow().purpose()).isEqualTo(RecoveryPurpose.ACTIVATION);
        activate(wrongEmail, oldCode, CHOSEN).andExpect(status().isBadRequest());
        activate(rightEmail, oldCode, CHOSEN).andExpect(status().isBadRequest());
        activate(rightEmail, codeOf(rightEmail), CHOSEN).andExpect(status().isNoContent());
        assertThat(signIn(rightEmail, CHOSEN).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void AC_514_anActiveAccountCannotBeResentACode() throws Exception {
        AccountPrincipal active = account(Role.GROOM);

        resend(manager, active.id()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_PENDING"));

        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void AC_515_aPendingPersonWhoseCodeExpiredUsesForgottenPassword() throws Exception {
        String email = uniqueEmail("het-han");
        created(email, Role.GROOM);
        String invitation = codeOf(email);
        TestClock.shift(Duration.ofHours(25));

        requestCode(email).andExpect(status().isOk());
        var sent = mailer.lastTo(email).orElseThrow();
        assertThat(sent.purpose()).isEqualTo(RecoveryPurpose.ACTIVATION);
        assertThat(sent.code()).isNotEqualTo(invitation);
        activate(email, invitation, CHOSEN).andExpect(status().isBadRequest());
        activate(email, sent.code(), CHOSEN).andExpect(status().isNoContent());

        assertThat(stored(email).isActive()).isTrue();
        assertThat(signIn(email, CHOSEN).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void AC_516_aDeactivatedPendingAccountStaysClosed() throws Exception {
        String email = uniqueEmail("huy");
        long id = created(email, Role.GROOM).get("id").asLong();
        String code = codeOf(email);

        as(manager, post("/api/accounts/" + id + "/deactivate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false)).andExpect(jsonPath("$.active").value(false));

        activate(email, code, CHOSEN).andExpect(status().isBadRequest());
        assertThat(stored(email).isActive()).isFalse();
        assertThat(stored(email).isPending()).isFalse();
        resend(manager, id).andExpect(status().isConflict());
    }

    @Test
    void aPersonOfAnotherActiveAccountStillResetsAPasswordWithTheShortCode() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);

        requestCode(groom.email()).andExpect(status().isOk());

        assertThat(mailer.lastTo(groom.email()).orElseThrow().purpose()).isEqualTo(RecoveryPurpose.RESET);
        assertThat(jdbc.queryForObject("select purpose from password_recovery where account_id = ?", String.class,
                groom.id())).isEqualTo("RESET");
    }

    @Test
    void AC_519_theScheduleOfAPendingGroomCanBeEditedButLeaveCannotBeRecorded() throws Exception {
        String email = uniqueEmail("lich");
        Map<String, Object> body = invite(email, Role.GROOM);
        body.put("schedule", List.of(Map.of("weekday", 1, "start", "05:00", "end", "12:00")));
        long id = body(asJson(manager, post("/api/accounts"), body).andExpect(status().isCreated())).get("id").asLong();

        asJson(manager, put("/api/accounts/" + id + "/schedule"),
                Map.of("ranges", List.of(Map.of("weekday", 2, "start", "06:00", "end", "11:00"))))
                .andExpect(status().isOk());

        JsonNode schedule = pageBody(as(manager, get("/api/accounts/" + id + "/schedule")).andExpect(status().isOk()));
        assertThat(schedule.get("ranges")).hasSize(1);
        assertThat(schedule.get("ranges").get(0).get("weekday").asInt()).isEqualTo(2);
        asJson(manager, post("/api/accounts/" + id + "/leave"),
                Map.of("date", LocalDate.now().plusDays(3).toString())).andExpect(status().isBadRequest());
    }

    @Test
    void aPendingAccountCannotChangeRole() throws Exception {
        String email = uniqueEmail("vai");
        long id = created(email, Role.GROOM).get("id").asLong();

        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("VETERINARIAN")))
                .andExpect(status().isBadRequest());
    }
}
