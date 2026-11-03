package vn.racehorse.account.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.racehorse.account.config.BootstrapProperties;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.config.ClubProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Task 3.1: the first Club Manager comes from configuration; start-up fails without a password (TECH-006). */
class BootstrapClubManagerTest {

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ClubClock clock = new ClubClock(new ClubProperties("Asia/Ho_Chi_Minh"));

    private BootstrapClubManager runner(String password) {
        return new BootstrapClubManager(accounts, new BCryptPasswordEncoder(),
                new BootstrapProperties("admin", password, "Quản lý"), audit, clock);
    }

    @Test
    void refusesToStartWithoutPasswordWhenNoClubManagerExists() {
        when(accounts.existsByRoleAndActiveTrue(Role.CLUB_MANAGER)).thenReturn(false);
        assertThatThrownBy(() -> runner("").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_PASSWORD");
        verify(accounts, never()).save(any());
    }

    @Test
    void createsClubManagerWhenPasswordConfigured() {
        when(accounts.existsByRoleAndActiveTrue(Role.CLUB_MANAGER)).thenReturn(false);
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        runner("secret-password").run(null);
        verify(accounts).save(any());
    }

    @Test
    void explainsTheLockOutWhenTheBootstrapNameIsTakenAndNoClubManagerIsActive() {
        when(accounts.existsByRoleAndActiveTrue(Role.CLUB_MANAGER)).thenReturn(false);
        when(accounts.existsByUsername("admin")).thenReturn(true);
        assertThatThrownBy(() -> runner("secret-password").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'admin' already exists")
                .hasMessageContaining("BOOTSTRAP_USERNAME");
        verify(accounts, never()).save(any());
    }

    @Test
    void doesNothingWhenAClubManagerExists() {
        when(accounts.existsByRoleAndActiveTrue(Role.CLUB_MANAGER)).thenReturn(true);
        runner("").run(null);
        verify(accounts, never()).save(any());
    }
}
