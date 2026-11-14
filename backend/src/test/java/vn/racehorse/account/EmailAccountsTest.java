package vn.racehorse.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;
import vn.racehorse.support.RecordingRecoveryMailer;

import java.time.Instant;
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

/** The email is the sign-in identifier of every account and the Club Manager keeps it right (BR-210 to BR-213, DEC-074). */
class EmailAccountsTest extends ApiTest {

    @Autowired
    RecordingRecoveryMailer mailer;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void emptyMailbox() {
        mailer.reset();
    }

    private MvcResult signIn(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", password)))).andReturn();
    }

    private AccountPrincipal accountWithoutEmail(Role role) {
        UserAccount saved = accounts.save(new UserAccount(null, "Cũ " + unique("x"),
                passwordEncoder.encode(PASSWORD), role, Instant.now()));
        return AccountPrincipal.of(saved);
    }

    private void deactivate(long id) {
        UserAccount account = accounts.findById(id).orElseThrow();
        account.deactivate();
        accounts.save(account);
    }

    private Map<String, Object> newAccount(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("displayName", "Nhân viên");
        body.put("password", PASSWORD);
        body.put("roles", List.of("GROOM"));
        return body;
    }

    private Map<String, Object> emailBody(String email, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("reason", reason);
        return body;
    }

    private List<Map<String, Object>> auditOf(long accountId, String action) {
        return jdbc.queryForList("select actor_id, details from audit_entry where target_id = ? and action = ? order by id",
                accountId, action);
    }

    @Test
    void AC_455_signsInWithTheEmailInAnotherCaseAndWithSpaces() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);

        MvcResult ok = signIn(" " + groom.email().toUpperCase() + " ", PASSWORD);

        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(ok.getResponse().getContentAsString()).get("email").asText()).isEqualTo(groom.email());
        assertThat(accounts.findById(groom.id()).orElseThrow().getEmail()).isEqualTo(groom.email());
        mvc.perform(get("/api/auth/me").session((MockHttpSession) ok.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(groom.email()))
                .andExpect(jsonPath("$.role").value("GROOM"));
    }

    @Test
    void AC_456_everyRefusalLooksTheSame() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);
        AccountPrincipal leaver = account(Role.GROOM);
        deactivate(leaver.id());

        MvcResult wrongPassword = signIn(groom.email(), "wrong-password");
        MvcResult unknownEmail = signIn("nobody@racehorse.example", PASSWORD);
        MvcResult notAnEmail = signIn("nobody", PASSWORD);
        MvcResult deactivated = signIn(leaver.email(), PASSWORD);

        for (MvcResult refused : List.of(wrongPassword, unknownEmail, notAnEmail, deactivated)) {
            assertThat(refused.getResponse().getStatus()).isEqualTo(401);
            assertThat(refused.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo(wrongPassword.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                    .contains("Sai email hoặc mật khẩu, hoặc tài khoản đã bị vô hiệu hóa.");
        }
    }

    @Test
    void AC_457_accountCreatedWithAnEmailSignsInWithItAndNoMailIsSent() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        String typed = uniqueEmail("An").toUpperCase();

        var created = body(asJson(manager, post("/api/accounts"), newAccount(typed)).andExpect(status().isCreated()));

        assertThat(created.get("email").asText()).isEqualTo(typed.toLowerCase());
        assertThat(signIn(typed.toLowerCase(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void AC_458_aDuplicateEmailIsRefusedInAnyCaseDeactivatedAccountsIncluded() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal existing = account(Role.GROOM);
        asJson(manager, post("/api/accounts"), newAccount(" " + existing.email().toUpperCase() + " "))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.fields.email").value("Email đã được dùng."));

        deactivate(existing.id());
        asJson(manager, post("/api/accounts"), newAccount(existing.email()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        assertThat(accounts.existsByEmail(existing.email())).isTrue();
    }

    @Test
    void AC_459_anInvalidOrMissingEmailIsRefusedNamingTheField() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long before = accounts.count();
        for (String bad : new String[]{"an.le@", "an le@example.com", "a@b", "a".repeat(245) + "@example.com", "no-at-sign"}) {
            asJson(manager, post("/api/accounts"), newAccount(bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.email").exists());
        }
        Map<String, Object> missing = newAccount(null);
        missing.remove("email");
        asJson(manager, post("/api/accounts"), missing).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").value("Bắt buộc nhập."));
        assertThat(accounts.count()).isEqualTo(before);
    }

    @Test
    void twoCreationsOfOneEmailAtTheSameMomentLeaveOne() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        String email = uniqueEmail("race");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return asJson(manager, post("/api/accounts"), newAccount(email)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (var f : results) {
                statuses.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnly(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theCurrentPersonAndTheAccountListCarryTheEmailAndNoPasswordData() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        MvcResult login = signIn(groom.email(), PASSWORD);
        String me = mvc.perform(get("/api/auth/me").session((MockHttpSession) login.getRequest().getSession(false)))
                .andReturn().getResponse().getContentAsString();
        assertThat(me).contains(groom.email()).doesNotContain("password").doesNotContain("$2");

        String list = as(manager, get("/api/accounts")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains(groom.email()).doesNotContain("password").doesNotContain("$2");
    }

    @Test
    void AC_468_theClubManagerEntersTheMissingEmail() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal old = accountWithoutEmail(Role.GROOM);
        String email = uniqueEmail("binh");

        var result = body(asJson(manager, put("/api/accounts/" + old.id() + "/email"), emailBody(email.toUpperCase(), null))
                .andExpect(status().isOk()));

        assertThat(result.get("email").asText()).isEqualTo(email);
        assertThat(signIn(email, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> audit = auditOf(old.id(), "ACCOUNT_EMAIL_SET");
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).get("actor_id")).isEqualTo(manager.id());
        assertThat(audit.get(0).get("details").toString()).contains(email);
    }

    @Test
    void anAccountWithoutAnEmailCannotSignInAndIsMarkedInTheList() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal old = accountWithoutEmail(Role.GROOM);
        assertThat(signIn("", PASSWORD).getResponse().getStatus()).isIn(400, 401);
        var list = body(as(manager, get("/api/accounts")).andExpect(status().isOk()));
        boolean found = false;
        for (var a : list) {
            if (a.get("id").asLong() == old.id()) {
                found = true;
                assertThat(a.get("email").isNull()).isTrue();
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void AC_484_theClubManagerCorrectsAnotherAccountsEmailWithAReason() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        String corrected = uniqueEmail("dung");

        asJson(manager, put("/api/accounts/" + groom.id() + "/email"), emailBody(corrected, " gõ nhầm khi tạo "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(corrected));

        assertThat(signIn(groom.email(), PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(signIn(corrected, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> audit = auditOf(groom.id(), "ACCOUNT_EMAIL_CORRECTED");
        assertThat(audit).hasSize(1);
        String details = audit.get(0).get("details").toString();
        assertThat(details).contains(groom.email()).contains(corrected).contains("gõ nhầm khi tạo");
        assertThat(audit.get(0).get("actor_id")).isEqualTo(manager.id());
    }

    @Test
    void AC_485_aCorrectionWithoutAReasonIsRefused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        for (String reason : new String[]{null, "", "   "}) {
            asJson(manager, put("/api/accounts/" + groom.id() + "/email"), emailBody(uniqueEmail("x"), reason))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"))
                    .andExpect(jsonPath("$.fields.reason").exists());
        }
        assertThat(accounts.findById(groom.id()).orElseThrow().getEmail()).isEqualTo(groom.email());
        assertThat(auditOf(groom.id(), "ACCOUNT_EMAIL_CORRECTED")).isEmpty();
    }

    @Test
    void AC_486_nobodyChangesTheirOwnEmail() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);

        asJson(manager, put("/api/accounts/" + manager.id() + "/email"), emailBody(uniqueEmail("me"), "muốn đổi"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SELF_EMAIL_CHANGE"))
                .andExpect(jsonPath("$.fields.id").value("Không thể tự đổi email của chính mình."));
        asJson(groom, put("/api/accounts/" + groom.id() + "/email"), emailBody(uniqueEmail("me"), "muốn đổi"))
                .andExpect(status().isForbidden());

        assertThat(accounts.findById(manager.id()).orElseThrow().getEmail()).isEqualTo(manager.email());
        assertThat(accounts.findById(groom.id()).orElseThrow().getEmail()).isEqualTo(groom.email());
    }

    @Test
    void onlyTheClubManagerEntersOrCorrectsAnEmail() throws Exception {
        AccountPrincipal vet = account(Role.VETERINARIAN);
        AccountPrincipal groom = account(Role.GROOM);
        asJson(vet, put("/api/accounts/" + groom.id() + "/email"), emailBody(uniqueEmail("x"), "lý do"))
                .andExpect(status().isForbidden());
        assertThat(accounts.findById(groom.id()).orElseThrow().getEmail()).isEqualTo(groom.email());
    }

    @Test
    void aDuplicateOrInvalidEmailIsRefusedWhenEnteredLater() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        AccountPrincipal other = account(Role.GROOM);
        asJson(manager, put("/api/accounts/" + groom.id() + "/email"), emailBody(other.email().toUpperCase(), "lý do"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        asJson(manager, put("/api/accounts/" + groom.id() + "/email"), emailBody("not-an-email", "lý do"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.email").exists());
        asJson(manager, put("/api/accounts/999999999/email"), emailBody(uniqueEmail("x"), "lý do"))
                .andExpect(status().isNotFound());
        assertThat(accounts.findById(groom.id()).orElseThrow().getEmail()).isEqualTo(groom.email());
    }

    @Test
    void askingForTheEmailAnAccountAlreadyHasChangesNothing() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        asJson(manager, put("/api/accounts/" + groom.id() + "/email"), emailBody(groom.email().toUpperCase(), null))
                .andExpect(status().isOk());
        assertThat(auditOf(groom.id(), "ACCOUNT_EMAIL_CORRECTED")).isEmpty();
    }
}
