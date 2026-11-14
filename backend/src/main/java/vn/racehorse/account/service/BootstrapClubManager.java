package vn.racehorse.account.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.EmailAddress;
import vn.racehorse.account.config.BootstrapProperties;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Only a Club Manager can create accounts (BR-090), so the first one is created from configuration when no active
 * Club Manager exists. Start-up fails if no password is configured (TECH-006). Since the sign-in identifier became the
 * email (DEC-074), an active Club Manager that has none adopts the configured email, so the club is never locked out
 * (BR-212, TECH-227).
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
        List<UserAccount> managers = accounts.findByRoleOrderByDisplayNameAsc(Role.CLUB_MANAGER).stream()
                .filter(UserAccount::isActive).toList();
        if (managers.isEmpty()) {
            create();
        } else if (managers.stream().noneMatch(m -> m.getEmail() != null)) {
            adopt(managers);
        }
    }

    private void create() {
        if (bootstrap.password() == null || bootstrap.password().isBlank()) {
            throw new IllegalStateException(
                    "No active Club Manager exists and BOOTSTRAP_PASSWORD is not set; refusing to start.");
        }
        EmailAddress email = configuredEmail("No active Club Manager exists");
        if (accounts.existsByEmail(email.value())) {
            // No active Club Manager, yet the email is taken (the account lost its role or was deactivated).
            // Say so instead of failing later with a database duplicate-key error; the operator decides (TECH-006).
            throw new IllegalStateException("No active Club Manager exists, but an account with the email '"
                    + email.value() + "' already exists with another role or is deactivated. The service will not "
                    + "guess: restore that account in the database (role CLUB_MANAGER, active = true; on Railway run "
                    + "scripts/railway-restore-admin.sh) or set BOOTSTRAP_EMAIL to an unused address, then start again.");
        }
        String displayName = bootstrap.displayName() == null || bootstrap.displayName().isBlank()
                ? email.value() : bootstrap.displayName();
        UserAccount manager = accounts.save(new UserAccount(email.value(), displayName,
                passwordEncoder.encode(bootstrap.password()), Role.CLUB_MANAGER, clock.now()));
        audit.recordSystem("ACCOUNT_BOOTSTRAPPED", "ACCOUNT", manager.getId(),
                Map.of("email", manager.getEmail(), "role", Role.CLUB_MANAGER.name()));
    }

    /** No active Club Manager has an email: give the configured one to the oldest of them (the password is untouched). */
    private void adopt(List<UserAccount> managers) {
        EmailAddress email = configuredEmail("An active Club Manager exists but has no email");
        if (accounts.existsByEmail(email.value())) {
            throw new IllegalStateException("An active Club Manager has no email, but the address '" + email.value()
                    + "' in BOOTSTRAP_EMAIL already belongs to another account. Set BOOTSTRAP_EMAIL to an unused "
                    + "address, then start again.");
        }
        Optional<UserAccount> oldest = managers.stream().min(Comparator.comparing(UserAccount::getId));
        UserAccount manager = oldest.orElseThrow();
        manager.changeEmail(email.value());
        accounts.saveAndFlush(manager);
        audit.recordSystem("ACCOUNT_EMAIL_SET", "ACCOUNT", manager.getId(), Map.of("email", email.value()));
    }

    private EmailAddress configuredEmail(String situation) {
        return EmailAddress.tryParse(bootstrap.email()).orElseThrow(() -> new IllegalStateException(
                situation + " and BOOTSTRAP_EMAIL is not set to a valid email address; refusing to start. Set "
                        + "BOOTSTRAP_EMAIL to the email the Club Manager signs in with."));
    }
}
