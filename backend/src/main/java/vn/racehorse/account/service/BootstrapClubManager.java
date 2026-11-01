package vn.racehorse.account.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.config.BootstrapProperties;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;

import java.util.Map;

/**
 * Only a Club Manager can create accounts (BR-090), so the first one is created from configuration
 * when no active Club Manager exists. Start-up fails if no password is configured (TECH-006).
 */
@Component
public class BootstrapClubManager implements ApplicationRunner {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrap;
    private final AuditService audit;
    private final ClubClock clock;

    public BootstrapClubManager(UserAccountRepository accounts, PasswordEncoder passwordEncoder,
                                BootstrapProperties bootstrap, AuditService audit, ClubClock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.bootstrap = bootstrap;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.existsByRoleAndActiveTrue(Role.CLUB_MANAGER)) {
            return;
        }
        if (bootstrap.password() == null || bootstrap.password().isBlank()) {
            throw new IllegalStateException(
                    "No active Club Manager exists and BOOTSTRAP_PASSWORD is not set; refusing to start.");
        }
        if (bootstrap.username() == null || bootstrap.username().isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_USERNAME must not be blank.");
        }
        if (accounts.existsByUsername(bootstrap.username())) {
            // No active Club Manager, yet the name is taken (the account lost its role or was deactivated).
            // Say so instead of failing later with a database duplicate-key error; the operator decides (TECH-006).
            throw new IllegalStateException("No active Club Manager exists, but an account named '" + bootstrap.username()
                    + "' already exists with another role or is deactivated. The service will not guess: restore that "
                    + "account in the database (role CLUB_MANAGER, active = true; on Railway run "
                    + "scripts/railway-restore-admin.sh) or set BOOTSTRAP_USERNAME to an unused name, then start again.");
        }
        String displayName = bootstrap.displayName() == null || bootstrap.displayName().isBlank()
                ? bootstrap.username() : bootstrap.displayName();
        UserAccount manager = accounts.save(new UserAccount(bootstrap.username(), displayName,
                passwordEncoder.encode(bootstrap.password()), Role.CLUB_MANAGER, clock.now()));
        audit.recordSystem("ACCOUNT_BOOTSTRAPPED", "ACCOUNT", manager.getId(),
                Map.of("username", manager.getUsername(), "role", Role.CLUB_MANAGER.name()));
    }
}
