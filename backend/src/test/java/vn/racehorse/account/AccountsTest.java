package vn.racehorse.account;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Accounts, one role per person, sign-in and deactivation (REQ-031, REQ-033, BR-090). */
class AccountsTest extends ApiTest {

    private MvcResult signIn(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", password))))
                .andReturn();
    }

    private JsonNode createAccount(AccountPrincipal manager, String role) throws Exception {
        return body(asJson(manager, post("/api/accounts"), Map.of(
                "email", uniqueEmail("staff"), "displayName", "Nhân viên mới", "password", PASSWORD,
                "roles", List.of(role))).andExpect(status().isCreated()));
    }

    /** Several callers creating one email at the same time: one account, the others get the duplicate answer (BR-090). */
    @Test
    void simultaneousCreatesOfOneEmailGiveOneAccountAndConflicts() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        int callers = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(callers);
        try {
            // One round can miss the window between the check and the insert; several rounds make the collision certain.
            for (int round = 0; round < 6; round++) {
                String email = uniqueEmail("race");
                java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(callers);
                java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
                List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
                for (int i = 0; i < callers; i++) {
                    results.add(pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        return asJson(manager, post("/api/accounts"), Map.of("email", email,
                                "displayName", "Tạo cùng lúc", "password", PASSWORD, "roles", List.of("GROOM")))
                                .andReturn().getResponse().getStatus();
                    }));
                }
                ready.await();
                start.countDown();
                List<Integer> statuses = new java.util.ArrayList<>();
                for (java.util.concurrent.Future<Integer> f : results) {
                    statuses.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
                }
                assertThat(statuses).as("round %d", round).containsOnly(201, 409);
                assertThat(statuses.stream().filter(s -> s == 201).count()).as("round %d", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private List<JsonNode> auditFor(AccountPrincipal manager, long accountId, String action) throws Exception {
        JsonNode all = body(as(manager, get("/api/audit").param("actorId", manager.id().toString())));
        return java.util.stream.StreamSupport.stream(all.spliterator(), false)
                .filter(e -> e.get("targetId").asLong() == accountId && e.get("action").asText().equals(action))
                .toList();
    }

    @Test
    void signInSucceedsWithCorrectPasswordAndFailsOtherwise() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);
        assertThat(signIn(groom.email(), "wrong-password").getResponse().getStatus()).isEqualTo(401);
        MvcResult ok = signIn(groom.email(), PASSWORD);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        mvc.perform(get("/api/auth/me").session((MockHttpSession) ok.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("GROOM"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void AC_020_accountCreatedWithOneRoleSignsInWithThatRoleOnly() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        JsonNode created = createAccount(manager, "VETERINARIAN");
        assertThat(created.get("role").asText()).isEqualTo("VETERINARIAN");

        MvcResult login = signIn(created.get("email").asText(), PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        String[] vetPermissions = java.util.Arrays.stream(vn.racehorse.security.Permission.values())
                .filter(p -> p.allows(Role.VETERINARIAN)).map(Enum::name).toArray(String[]::new);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.role").value("VETERINARIAN"))
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.containsInAnyOrder(vetPermissions)))
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("MANAGE_ACCOUNTS"))));
        mvc.perform(get("/api/horses").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/accounts").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").session(session)).andExpect(status().isForbidden());
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private interface Body {
        void run() throws Exception;
    }

    /** Runs the body while only the given accounts are active Club Managers (the database is shared by all tests). */
    private void onlyClubManagers(List<Long> keep, Body body) throws Exception {
        List<Long> others = jdbc.queryForList(
                "select id from user_account where role = 'CLUB_MANAGER' and active", Long.class)
                .stream().filter(id -> !keep.contains(id)).toList();
        others.forEach(id -> jdbc.update("update user_account set active = false where id = ?", id));
        try {
            body.run();
        } finally {
            others.forEach(id -> jdbc.update("update user_account set active = true where id = ?", id));
        }
    }

    private String roleOf(Long id) {
        return jdbc.queryForObject("select role from user_account where id = ?", String.class, id);
    }

    @Test
    void P06_selfRoleChangeRefused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        asJson(manager, put("/api/accounts/" + manager.id() + "/roles"), Map.of("roles", List.of("GROOM")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ROLE_CHANGE"))
                .andExpect(jsonPath("$.message").value("Không thể tự đổi vai trò của chính mình."));
        assertThat(roleOf(manager.id())).isEqualTo("CLUB_MANAGER");
        assertThat(auditFor(manager, manager.id(), "ROLE_CHANGED")).isEmpty();
    }

    @Test
    void P06_otherRoleChangeStillWorks() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "GROOM").get("id").asLong();
        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("VETERINARIAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VETERINARIAN"));
        assertThat(auditFor(manager, id, "ROLE_CHANGED")).hasSize(1);
    }

    /** Two Club Managers act on each other at the same moment; at least one active Club Manager must remain (TECH-121). */
    private void raceClubManagersDeactivatingEachOther() throws Exception {
        AccountPrincipal a = account(Role.CLUB_MANAGER);
        AccountPrincipal b = account(Role.CLUB_MANAGER);
        onlyClubManagers(List.of(a.id(), b.id()), () -> {
            for (int round = 0; round < 6; round++) {
                jdbc.update("update user_account set role = 'CLUB_MANAGER', active = true where id in (?, ?)", a.id(), b.id());
                java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
                try {
                    List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
                    for (AccountPrincipal[] pair : new AccountPrincipal[][] {{a, b}, {b, a}}) {
                        results.add(pool.submit(() -> {
                            go.await();
                            return as(pair[0], post("/api/accounts/" + pair[1].id() + "/deactivate"))
                                    .andReturn().getResponse().getStatus();
                        }));
                    }
                    go.countDown();
                    int ok = 0;
                    for (var f : results) {
                        if (f.get() == 200) {
                            ok++;
                        }
                    }
                    assertThat(ok).as("both requests succeeded in round " + round).isLessThanOrEqualTo(1);
                } finally {
                    pool.shutdownNow();
                }
                assertThat(jdbc.queryForObject(
                        "select count(*) from user_account where role = 'CLUB_MANAGER' and active and id in (?, ?)",
                        Integer.class, a.id(), b.id())).as("active Club Managers in round " + round).isGreaterThanOrEqualTo(1);
            }
        });
    }

    @Test
    void P06_clubManagerRoleIsLockedEvenForAnotherClubManager() throws Exception {
        AccountPrincipal a = account(Role.CLUB_MANAGER);
        AccountPrincipal b = account(Role.CLUB_MANAGER);
        asJson(a, put("/api/accounts/" + b.id() + "/roles"), Map.of("roles", List.of("HEAD_TRAINER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLUB_MANAGER_ROLE_LOCKED"))
                .andExpect(jsonPath("$.message").value("Không thể đổi vai trò của tài khoản Quản lý CLB."));
        assertThat(roleOf(b.id())).isEqualTo("CLUB_MANAGER");
        assertThat(auditFor(a, b.id(), "ROLE_CHANGED")).isEmpty();
    }

    @Test
    void P06_anotherClubManagerCanStillBeDeactivated() throws Exception {
        AccountPrincipal a = account(Role.CLUB_MANAGER);
        AccountPrincipal b = account(Role.CLUB_MANAGER);
        onlyClubManagers(List.of(a.id(), b.id()), () -> {
            as(a, post("/api/accounts/" + b.id() + "/deactivate")).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("select active from user_account where id = ?", Boolean.class, b.id()))
                    .isFalse();
        });
    }

    @Test
    void P06_lastClubManagerCannotBeDeactivated() throws Exception {
        raceClubManagersDeactivatingEachOther();
    }

    private void assertRoleUnchangedAndUnaudited(AccountPrincipal manager, long id, String role) throws Exception {
        assertThat(roleOf(id)).isEqualTo(role);
        assertThat(auditFor(manager, id, "ROLE_CHANGED")).isEmpty();
    }

    @Test
    void P06_unknownRoleValueRefusedWithAVietnamese400() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "GROOM").get("id").asLong();
        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("SUPERUSER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Dữ liệu gửi lên không đúng định dạng."));
        assertRoleUnchangedAndUnaudited(manager, id, "GROOM");
    }

    @Test
    void P06_missingEmptyNullAndSeveralRolesRefused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "GROOM").get("id").asLong();
        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        for (Object roles : new Object[] {List.of(), java.util.Collections.singletonList(null),
                List.of("VETERINARIAN", "HEAD_TRAINER")}) {
            asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", roles))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ONE_ROLE_ONLY"))
                    .andExpect(jsonPath("$.message").value("Mỗi người chỉ có một vai trò."));
        }
        assertRoleUnchangedAndUnaudited(manager, id, "GROOM");
    }

    @Test
    void P06_unknownAccountIs404() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        asJson(manager, put("/api/accounts/987654321/roles"), Map.of("roles", List.of("GROOM")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void P06_deactivatedAccountRoleCannotBeChanged() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "GROOM").get("id").asLong();
        as(manager, post("/api/accounts/" + id + "/deactivate")).andExpect(status().isOk());
        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("VETERINARIAN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.message").value("Không thể đổi vai trò của tài khoản đã bị vô hiệu hóa."));
        assertRoleUnchangedAndUnaudited(manager, id, "GROOM");
    }

    @Test
    void P06_sameRoleRequestIsANoOpWithoutAudit() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal other = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "GROOM").get("id").asLong();
        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("GROOM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("GROOM"));
        assertRoleUnchangedAndUnaudited(manager, id, "GROOM");
        asJson(manager, put("/api/accounts/" + other.id() + "/roles"), Map.of("roles", List.of("CLUB_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLUB_MANAGER"));
        assertRoleUnchangedAndUnaudited(manager, other.id(), "CLUB_MANAGER");
    }

    @Test
    void P06_selfCheckUsesTheSessionNeverTheRequestBody() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal other = account(Role.CLUB_MANAGER);
        // the body claims to be about somebody else; the path id equals the signed-in account, so it is a self change
        asJson(manager, put("/api/accounts/" + manager.id() + "/roles"),
                Map.of("roles", List.of("GROOM"), "id", other.id(), "accountId", other.id(), "actorId", other.id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ROLE_CHANGE"));
        assertRoleUnchangedAndUnaudited(manager, manager.id(), "CLUB_MANAGER");
    }

    @Test
    void P06_promotionToClubManagerIsAllowedAndThenLocked() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "GROOM").get("id").asLong();
        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("CLUB_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLUB_MANAGER"));
        List<JsonNode> entries = auditFor(manager, id, "ROLE_CHANGED");
        assertThat(entries).hasSize(1);
        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("GROOM")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLUB_MANAGER_ROLE_LOCKED"));
        assertThat(roleOf(id)).isEqualTo("CLUB_MANAGER");
        assertThat(auditFor(manager, id, "ROLE_CHANGED")).hasSize(1);
    }

    @Test
    void P06_responsesNeverLeakThePasswordHash() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "GROOM").get("id").asLong();
        List<String> responses = new java.util.ArrayList<>();
        responses.add(asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("VETERINARIAN")))
                .andReturn().getResponse().getContentAsString());
        responses.add(asJson(manager, put("/api/accounts/" + manager.id() + "/roles"), Map.of("roles", List.of("GROOM")))
                .andReturn().getResponse().getContentAsString());
        responses.add(as(manager, get("/api/accounts")).andReturn().getResponse().getContentAsString());
        for (String text : responses) {
            assertThat(text).doesNotContain("passwordHash").doesNotContain("password_hash").doesNotContain("$2a$")
                    .doesNotContain("$2b$").doesNotContain(PASSWORD);
        }
    }

    @Test
    void P06_callerWithoutPermissionGets403BeforeAnyValidationMessage() throws Exception {
        AccountPrincipal head = account(Role.HEAD_TRAINER);
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "GROOM").get("id").asLong();
        // invalid body, unknown role, unknown account, own id: all the same refusal
        for (var request : List.of(
                put("/api/accounts/" + id + "/roles"),
                put("/api/accounts/987654321/roles"),
                put("/api/accounts/" + head.id() + "/roles"))) {
            for (Object body : new Object[] {Map.of("roles", List.of("SUPERUSER")), Map.of(), Map.of("roles", List.of())}) {
                asJson(head, request, body)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            }
        }
        asJson(head, post("/api/accounts"), Map.of()).andExpect(status().isForbidden());
        as(head, post("/api/accounts/987654321/deactivate")).andExpect(status().isForbidden());
        assertRoleUnchangedAndUnaudited(manager, id, "GROOM");
    }

    @Test
    void P06_inactiveClubManagersAreNotCounted() throws Exception {
        AccountPrincipal active = account(Role.CLUB_MANAGER);
        AccountPrincipal inactive = account(Role.CLUB_MANAGER);
        jdbc.update("update user_account set active = false where id = ?", inactive.id());
        List<Long> ids = tx.execute(s -> accounts.lockActiveClubManagers().stream().map(UserAccount::getId).toList());
        assertThat(ids).contains(active.id()).doesNotContain(inactive.id());
    }

    @Test
    void AC_125_managerCannotDeactivateOwnAccount() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        as(manager, post("/api/accounts/" + manager.id() + "/deactivate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_DEACTIVATION"));
        assertThat(accounts.findById(manager.id()).orElseThrow().isActive()).isTrue();
        assertThat(auditFor(manager, manager.id(), "ACCOUNT_DEACTIVATED")).isEmpty();
    }

    @Test
    void AC_021_deactivatedAccountCannotSignInHistoryKeptAndAudited() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        JsonNode created = createAccount(manager, "HEAD_TRAINER");
        long id = created.get("id").asLong();
        MockHttpSession openSession = (MockHttpSession) signIn(created.get("email").asText(), PASSWORD)
                .getRequest().getSession(false);

        as(manager, post("/api/accounts/" + id + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/auth/me").session(openSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DEACTIVATED"));
        assertThat(signIn(created.get("email").asText(), PASSWORD).getResponse().getStatus()).isEqualTo(401);

        JsonNode listed = body(as(manager, get("/api/accounts")));
        assertThat(listed.findValues("id").stream().map(JsonNode::asLong)).contains(id);
        assertThat(auditFor(manager, id, "ACCOUNT_CREATED")).hasSize(1);
        assertThat(auditFor(manager, id, "ACCOUNT_DEACTIVATED")).hasSize(1);
    }

    @Test
    void AC_022_secondRoleForOnePersonRefused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "HEAD_TRAINER").get("id").asLong();

        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("HEAD_TRAINER", "VETERINARIAN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONE_ROLE_ONLY"))
                .andExpect(jsonPath("$.fields.roles").value("Mỗi người chỉ có một vai trò."));
        asJson(manager, post("/api/accounts"), Map.of("email", uniqueEmail("two"), "displayName", "X",
                "password", PASSWORD, "roles", List.of("GROOM", "VETERINARIAN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONE_ROLE_ONLY"));
        assertThat(accounts.findById(id).orElseThrow().getRole()).isEqualTo(Role.HEAD_TRAINER);
    }

    @Test
    void AC_023_veterinarianCannotCreateOrChangeAccounts() throws Exception {
        AccountPrincipal vet = account(Role.VETERINARIAN);
        AccountPrincipal other = account(Role.GROOM);
        asJson(vet, post("/api/accounts"), Map.of("email", uniqueEmail("x"), "displayName", "X",
                "password", PASSWORD, "roles", List.of("GROOM")))
                .andExpect(status().isForbidden());
        asJson(vet, put("/api/accounts/" + other.id() + "/roles"), Map.of("roles", List.of("CLUB_MANAGER")))
                .andExpect(status().isForbidden());
        as(vet, post("/api/accounts/" + other.id() + "/deactivate")).andExpect(status().isForbidden());
        assertThat(accounts.findById(other.id()).orElseThrow().getRole()).isEqualTo(Role.GROOM);
    }

    @Test
    void AC_024_roleChangeAuditedWithOldAndNewRole() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long id = createAccount(manager, "GROOM").get("id").asLong();

        asJson(manager, put("/api/accounts/" + id + "/roles"), Map.of("roles", List.of("HEAD_TRAINER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HEAD_TRAINER"));

        List<JsonNode> entries = auditFor(manager, id, "ROLE_CHANGED");
        assertThat(entries).hasSize(1);
        JsonNode entry = entries.get(0);
        JsonNode details = json.readTree(entry.get("details").asText());
        assertThat(details.get("oldRole").asText()).isEqualTo("GROOM");
        assertThat(details.get("newRole").asText()).isEqualTo("HEAD_TRAINER");
        assertThat(entry.get("actorName").asText()).isEqualTo(manager.displayName());
        assertThat(entry.get("occurredAt").asText()).isNotBlank();
    }
}
