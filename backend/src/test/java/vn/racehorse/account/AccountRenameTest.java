package vn.racehorse.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import vn.racehorse.account.entity.Role;
import vn.racehorse.audit.service.AuditActionLabels;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Club Manager renames any account, including their own (BR-239, DEC-077). */
class AccountRenameTest extends ApiTest {

    @Autowired
    JdbcTemplate jdbc;

    private org.springframework.test.web.servlet.ResultActions rename(AccountPrincipal who, long id, Object name)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("displayName", name);
        return asJson(who, put("/api/accounts/" + id + "/name"), body);
    }

    private List<Map<String, Object>> auditOf(long id) {
        return jdbc.queryForList("select actor_id, details from audit_entry where target_id = ? "
                + "and action = 'ACCOUNT_NAME_CHANGED' order by id", id);
    }

    private String nameOf(long id) {
        return accounts.findById(id).orElseThrow().getDisplayName();
    }

    @Test
    void AC_521_theNameIsCorrectedAndNothingElseChanges() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);

        rename(manager, groom.id(), "Nguyễn Văn A").andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var after = accounts.findById(groom.id()).orElseThrow();
        assertThat(after.getDisplayName()).isEqualTo("Nguyễn Văn A");
        assertThat(after.getEmail()).isEqualTo(groom.email());
        assertThat(after.getRole()).isEqualTo(Role.GROOM);
        assertThat(after.isActive()).isTrue();
        var list = body(as(manager, get("/api/accounts")).andExpect(status().isOk()));
        boolean listed = false;
        for (var a : list) {
            if (a.get("id").asLong() == groom.id()) {
                listed = a.get("displayName").asText().equals("Nguyễn Văn A");
            }
        }
        assertThat(listed).isTrue();
    }

    @Test
    void AC_522_theClubManagerRenamesTheirOwnAccountAndMeShowsItAtOnce() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", manager.email(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        mvc.perform(put("/api/accounts/" + manager.id() + "/name").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("displayName", "Quản lý Mới")))).andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Quản lý Mới"));
    }

    @Test
    void AC_523_anyStateAndAnyRoleCanBeRenamed() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal other = account(Role.CLUB_MANAGER);
        AccountPrincipal gone = account(Role.VETERINARIAN);
        var g = accounts.findById(gone.id()).orElseThrow();
        g.deactivate();
        accounts.save(g);
        var pending = accounts.save(vn.racehorse.account.entity.UserAccount.invited(uniqueEmail("cho"), "Chờ",
                passwordEncoder.encode("x-unusable-1"), Role.GROOM, java.time.Instant.now()));

        rename(manager, other.id(), "Quản lý khác").andExpect(status().isOk());
        rename(manager, gone.id(), "Đã nghỉ").andExpect(status().isOk());
        rename(manager, pending.getId(), "Chờ kích hoạt tên mới").andExpect(status().isOk());

        assertThat(accounts.findById(other.id()).orElseThrow().getRole()).isEqualTo(Role.CLUB_MANAGER);
        assertThat(accounts.findById(gone.id()).orElseThrow().isActive()).isFalse();
        var p = accounts.findById(pending.getId()).orElseThrow();
        assertThat(p.isPending()).isTrue();
        assertThat(p.isActive()).isFalse();
    }

    @Test
    void AC_524_everyOtherRoleIsRefusedFirstEvenWithABadBodyOrAnUnknownAccount() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);
        for (Role role : new Role[]{Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER}) {
            AccountPrincipal who = account(role);
            String before = nameOf(groom.id());
            rename(who, groom.id(), "Tên mới").andExpect(status().isForbidden());
            rename(who, groom.id(), "   ").andExpect(status().isForbidden());
            rename(who, 999_999_999L, null).andExpect(status().isForbidden());
            assertThat(nameOf(groom.id())).isEqualTo(before);
        }
        assertThat(auditOf(groom.id())).isEmpty();
    }

    @Test
    void AC_525_anUnknownAccountIsNotFound() throws Exception {
        rename(account(Role.CLUB_MANAGER), 999_999_999L, "Tên").andExpect(status().isNotFound());
    }

    @Test
    void AC_526_aMissingEmptyOrBlankNameIsRefusedNamingTheField() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        String before = nameOf(groom.id());
        for (String name : new String[]{null, "", "   "}) {
            rename(manager, groom.id(), name).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.displayName").exists());
        }
        assertThat(nameOf(groom.id())).isEqualTo(before);
        assertThat(auditOf(groom.id())).isEmpty();
    }

    @Test
    void AC_527_twoHundredCharactersAreAcceptedAndTwoHundredAndOneAreRefused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        String before = nameOf(groom.id());

        rename(manager, groom.id(), "a".repeat(201)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.displayName").exists());
        assertThat(nameOf(groom.id())).isEqualTo(before);
        assertThat(auditOf(groom.id())).isEmpty();

        rename(manager, groom.id(), "a".repeat(200)).andExpect(status().isOk());
        assertThat(nameOf(groom.id())).hasSize(200);
        // Spaces count after tidying, not before.
        rename(manager, groom.id(), "b" + " ".repeat(50) + "c").andExpect(status().isOk());
        assertThat(nameOf(groom.id())).isEqualTo("b c");
    }

    @Test
    void AC_528_spacesAreTidied() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        rename(manager, groom.id(), "  Tran   Thi  B  ").andExpect(status().isOk());
        assertThat(nameOf(groom.id())).isEqualTo("Tran Thi B");
    }

    @Test
    void AC_529_theSameNameChangesNothingAndADuplicateNameIsAccepted() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        AccountPrincipal vet = account(Role.VETERINARIAN);
        rename(manager, groom.id(), "Le Van C").andExpect(status().isOk());
        assertThat(auditOf(groom.id())).hasSize(1);

        rename(manager, groom.id(), "Le Van C").andExpect(status().isOk());
        rename(manager, groom.id(), " Le  Van C ").andExpect(status().isOk());
        assertThat(auditOf(groom.id())).hasSize(1);

        rename(manager, vet.id(), "Le Van C").andExpect(status().isOk());
        assertThat(nameOf(vet.id())).isEqualTo("Le Van C");
    }

    @Test
    void AC_530_theRenameIsAuditedWithOldAndNewNameAndEarlierEntriesAreKept() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        String old = nameOf(groom.id());
        rename(manager, groom.id(), "Tên Một").andExpect(status().isOk());
        rename(manager, groom.id(), "Tên Hai").andExpect(status().isOk());

        List<Map<String, Object>> audit = auditOf(groom.id());
        assertThat(audit).hasSize(2);
        assertThat(audit.get(0).get("actor_id")).isEqualTo(manager.id());
        assertThat(audit.get(0).get("details").toString()).contains(old).contains("Tên Một");
        // The first entry is not rewritten by the second rename.
        assertThat(audit.get(0).get("details").toString()).doesNotContain("Tên Hai");
        assertThat(audit.get(1).get("details").toString()).contains("Tên Một").contains("Tên Hai");
        assertThat(AuditActionLabels.LABELS).containsEntry("ACCOUNT_NAME_CHANGED", "Đổi tên tài khoản");
    }
}
