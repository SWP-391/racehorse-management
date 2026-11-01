package vn.racehorse.account.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.events.DomainEvents.GroomUnavailable;
import vn.racehorse.schedule.service.GroomScheduleService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.schedule.dto.GroomScheduleRangeInput;

import java.util.List;
import java.util.Map;

/** Accounts with exactly one role, managed by the Club Manager; deactivated, never deleted (BR-090). */
@Service
public class AccountService {

    static final String ONE_ROLE_ONLY = "Mỗi người chỉ có một vai trò.";

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final ApplicationEventPublisher events;
    private final GroomScheduleService schedules;

    public AccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AccessGuard access,
                          AuditService audit, ClubClock clock, ApplicationEventPublisher events,
                          GroomScheduleService schedules) {
        this.events = events;
        this.schedules = schedules;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> list(Role role) {
        access.require(Permission.MANAGE_ACCOUNTS);
        return role == null ? accounts.findAllByOrderByDisplayNameAsc() : accounts.findByRoleOrderByDisplayNameAsc(role);
    }

    @Transactional
    public UserAccount create(String username, String displayName, String password, List<Role> roles) {
        return create(username, displayName, password, roles, null);
    }

    /** A Groom may come with the fixed weekly barn hours; an invalid schedule creates no account (BR-166). */
    @Transactional
    public UserAccount create(String username, String displayName, String password, List<Role> roles,
                              List<GroomScheduleRangeInput> schedule) {
        AccountPrincipal actor = access.require(Permission.MANAGE_ACCOUNTS);
        Role role = singleRole(roles);
        if (schedule != null && !schedule.isEmpty()) {
            if (role != Role.GROOM) {
                throw ApiException.invalid("VALIDATION_FAILED", "schedule", "Chỉ Groom mới có lịch làm.");
            }
            GroomScheduleService.parse(schedule);
        }
        if (accounts.existsByUsername(username)) {
            throw duplicateUsername();
        }
        UserAccount account;
        try {
            account = accounts.saveAndFlush(new UserAccount(username, displayName,
                    passwordEncoder.encode(password), role, clock.now()));
        } catch (DataIntegrityViolationException e) {
            // A concurrent create of the same name won the unique index between the check above and this insert (BR-090).
            throw duplicateUsername();
        }
        audit.record(actor, "ACCOUNT_CREATED", "ACCOUNT", account.getId(),
                Map.of("username", username, "role", role.name()));
        if (schedule != null && !schedule.isEmpty()) {
            schedules.setOnCreate(actor, account, schedule);
        }
        return account;
    }

    private static ApiException duplicateUsername() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_USERNAME", "Tên đăng nhập đã tồn tại.",
                Map.of("username", "Tên đăng nhập đã tồn tại."));
    }

    @Transactional
    public UserAccount changeRole(Long id, List<Role> roles) {
        AccountPrincipal actor = access.require(Permission.MANAGE_ACCOUNTS);
        Role role = singleRole(roles);
        if (actor.id().equals(id)) {
            // P-06 (extends DEC-031): a Club Manager cannot change their own role.
            throw ApiException.invalid("SELF_ROLE_CHANGE", "id", "Không thể tự đổi vai trò của chính mình.");
        }
        // The target row is locked, so the role checked below is the stored one and concurrent changes take turns.
        UserAccount account = accounts.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("tài khoản"));
        if (!account.isActive()) {
            throw ApiException.invalid("ACCOUNT_INACTIVE", "id",
                    "Không thể đổi vai trò của tài khoản đã bị vô hiệu hóa.");
        }
        Role old = account.getRole();
        if (old == role) {
            return account; // not a change: no audit entry, no error
        }
        if (old == Role.CLUB_MANAGER) {
            // The owner's rule: the Club Manager is the highest role; its role is never changed through the API.
            throw ApiException.invalid("CLUB_MANAGER_ROLE_LOCKED", "id", "Không thể đổi vai trò của tài khoản Quản lý CLB.");
        }
        account.changeRole(role);
        audit.record(actor, "ROLE_CHANGED", "ACCOUNT", account.getId(),
                Map.of("username", account.getUsername(), "oldRole", old.name(), "newRole", role.name()));
        if (old == Role.GROOM) {
            schedules.clearOnRoleChange(actor, account);
            // BR-136: the Head Trainer is asked to reassign the sessions this Groom holds
            events.publishEvent(new GroomUnavailable(account.getId(), account.getDisplayName(),
                    "không còn là Groom (đổi vai trò)"));
        }
        return account;
    }

    @Transactional
    public UserAccount deactivate(Long id) {
        AccountPrincipal actor = access.require(Permission.MANAGE_ACCOUNTS);
        if (actor.id().equals(id)) {
            // DEC-031: a Club Manager cannot deactivate their own account.
            throw ApiException.invalid("SELF_DEACTIVATION", "id", "Không thể tự vô hiệu hóa tài khoản của chính mình.");
        }
        // Lock order: the active Club Managers (by id) first, then the target row, so two requests cannot lock crosswise.
        List<UserAccount> managers = accounts.lockActiveClubManagers();
        UserAccount account = accounts.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("tài khoản"));
        if (account.isActive()) {
            if (account.getRole() == Role.CLUB_MANAGER) {
                requireAnotherClubManager(account, managers);
            }
            account.deactivate();
            audit.record(actor, "ACCOUNT_DEACTIVATED", "ACCOUNT", account.getId(),
                    Map.of("username", account.getUsername()));
            if (account.getRole() == Role.GROOM) {
                events.publishEvent(new GroomUnavailable(account.getId(), account.getDisplayName(),
                        "đã bị vô hiệu hóa"));
            }
        }
        return account;
    }

    /** P-06, TECH-121: the system keeps at least one active Club Manager; the lock makes concurrent requests take turns. */
    private static void requireAnotherClubManager(UserAccount target, List<UserAccount> activeManagers) {
        if (activeManagers.stream().noneMatch(m -> !m.getId().equals(target.getId()))) {
            throw ApiException.invalid("LAST_CLUB_MANAGER", "id", "Hệ thống phải còn ít nhất một Quản lý CLB đang hoạt động.");
        }
    }

    private static Role singleRole(List<Role> roles) {
        if (roles == null || roles.size() != 1 || roles.get(0) == null) {
            throw ApiException.invalid("ONE_ROLE_ONLY", "roles", ONE_ROLE_ONLY);
        }
        return roles.get(0);
    }
}
