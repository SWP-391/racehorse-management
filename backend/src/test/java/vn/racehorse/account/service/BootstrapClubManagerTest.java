package vn.racehorse.account.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.racehorse.account.config.BootstrapProperties;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.config.ClubProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The first Club Manager comes from configuration; start-up fails without a password (TECH-006). Since the email is the
 * sign-in identifier (DEC-074) a Club Manager without one adopts the configured email (BR-212, TECH-227).
 */
class BootstrapClubManagerTest {

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ClubClock clock = new ClubClock(new ClubProperties("Asia/Ho_Chi_Minh"));

    private BootstrapClubManager runner(String email, String password) {
        return new BootstrapClubManager(accounts, new BCryptPasswordEncoder(),
                new BootstrapProperties(email, password, "Quản lý"), audit, clock);
    }

    private static UserAccount manager(String email) {
        return new UserAccount(email, "Quản lý", "hash", Role.CLUB_MANAGER, Instant.now());
    }

    private void managers(UserAccount... list) {
        when(accounts.findByRoleOrderByDisplayNameAsc(Role.CLUB_MANAGER)).thenReturn(List.of(list));
    }

    @Test
    void refusesToStartWithoutPasswordWhenNoClubManagerExists() {
        managers();
        assertThatThrownBy(() -> runner("admin@racehorse.example", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_PASSWORD");
        verify(accounts, never()).save(any());
    }

    @Test
    void createsClubManagerWithTheConfiguredEmailWhenPasswordConfigured() {
        managers();
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        runner(" Admin@Racehorse.Example ", "secret-password").run(null);
        verify(accounts).save(org.mockito.ArgumentMatchers.argThat(a ->
                a.getEmail().equals("admin@racehorse.example") && a.getRole() == Role.CLUB_MANAGER));
    }

    @Test
    void explainsTheLockOutWhenTheBootstrapEmailIsTakenAndNoClubManagerIsActive() {
        managers();
        when(accounts.existsByEmail("admin@racehorse.example")).thenReturn(true);
        assertThatThrownBy(() -> runner("admin@racehorse.example", "secret-password").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'admin@racehorse.example'")
                .hasMessageContaining("BOOTSTRAP_EMAIL");
        verify(accounts, never()).save(any());
    }

    @Test
    void refusesToStartWhenAnEmailIsNeededButNoneIsConfigured() {
        managers();
        assertThatThrownBy(() -> runner("", "secret-password").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_EMAIL");
        verify(accounts, never()).save(any());
    }

    @Test
    void anExistingClubManagerWithoutAnEmailAdoptsTheConfiguredOne() {
        UserAccount older = manager(null);
        org.springframework.test.util.ReflectionTestUtils.setField(older, "id", 1L);
        UserAccount newer = manager(null);
        org.springframework.test.util.ReflectionTestUtils.setField(newer, "id", 2L);
        managers(newer, older);
        runner("Admin@Racehorse.Example", "").run(null);
        assertThat(older.getEmail()).isEqualTo("admin@racehorse.example");
        assertThat(newer.getEmail()).isNull();
        assertThat(older.getPasswordHash()).isEqualTo("hash");
        verify(accounts).saveAndFlush(older);
        verify(audit).recordSystem(eq("ACCOUNT_EMAIL_SET"), eq("ACCOUNT"), any(),
                eq(Map.of("email", "admin@racehorse.example")));
        verify(accounts, never()).save(any());
    }

    @Test
    void aClubManagerWithoutAnEmailAndNoConfiguredEmailStopsTheStart() {
        managers(manager(null));
        assertThatThrownBy(() -> runner("not-an-email", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_EMAIL");
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void adoptionRefusesAnEmailThatBelongsToAnotherAccount() {
        managers(manager(null));
        when(accounts.existsByEmail("admin@racehorse.example")).thenReturn(true);
        assertThatThrownBy(() -> runner("admin@racehorse.example", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another account");
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void doesNothingWhenAClubManagerAlreadyHasAnEmail() {
        managers(manager("boss@racehorse.example"));
        runner("", "").run(null);
        verify(accounts, never()).save(any());
        verify(accounts, never()).saveAndFlush(any());
    }
}
