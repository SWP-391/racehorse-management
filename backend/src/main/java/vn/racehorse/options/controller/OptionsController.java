package vn.racehorse.options.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.account.service.AccountService;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.common.paging.Search;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.service.HorseService;
import vn.racehorse.incident.service.IncidentService;
import vn.racehorse.options.dto.OptionDto;
import vn.racehorse.ration.service.RationService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.training.service.SessionService;

import java.util.List;
import java.util.function.Function;

/**
 * Choices for the search-box selects of the client (TECH-178). Each kind delegates to the service that owns the
 * data, so the permission and the visibility of that list apply unchanged: an Owner gets no horse but their own,
 * a Groom gets the minimal horse list (PERM-002), and a role without the permission is refused.
 */
@Tag(name = "Lựa chọn", description = "Danh sách lựa chọn cho các ô chọn có tìm kiếm")
@RestController
@RequestMapping("/api/options")
public class OptionsController {

    private final HorseService horses;
    private final IncidentService incidents;
    private final AccountService accounts;
    private final SessionService sessions;
    private final RationService rations;
    private final UserAccountRepository accountRepository;
    private final AccessGuard access;

    public OptionsController(HorseService horses, IncidentService incidents, AccountService accounts,
                             SessionService sessions, RationService rations,
                             UserAccountRepository accountRepository, AccessGuard access) {
        this.horses = horses;
        this.incidents = incidents;
        this.accounts = accounts;
        this.sessions = sessions;
        this.rations = rations;
        this.accountRepository = accountRepository;
        this.access = access;
    }

    @Operation(summary = "Lựa chọn theo loại")
    @GetMapping("/{kind}")
    public PageResponse<OptionDto> options(@PathVariable String kind,
                                           @RequestParam(required = false) Role role,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        List<OptionDto> all = switch (kind) {
            case "horses" -> horseOptions();
            case "owners" -> accountOptions(horsesOwners());
            case "trainers" -> accountOptions(headTrainers());
            case "grooms" -> accountOptions(sessions.staff());
            case "staff" -> accountOptions(staffAccounts());
            case "accounts" -> accountOptions(accounts.list(role));
            case "feed" -> rations.supplyOptions().stream()
                    .map(i -> new OptionDto(i.getId(), i.getName(), i.getUnit())).toList();
            default -> throw ApiException.notFound("danh sách lựa chọn");
        };
        return Paging.page(all.stream().filter(o -> Search.matches(params.q(), o.label(), o.hint())).toList(), params);
    }

    /** A Groom has the minimal list of PERM-002; everyone else the horses they may see, archived ones left out. */
    private List<OptionDto> horseOptions() {
        AccountPrincipal user = access.current();
        if (user.role() == Role.GROOM) {
            return incidents.horseOptions().stream()
                    .map(h -> new OptionDto(h.id(), h.name(), h.registrationNumber())).toList();
        }
        return horses.list().stream().filter(h -> !h.isArchived()).map(OptionsController::horseOption).toList();
    }

    private static OptionDto horseOption(Horse h) {
        return new OptionDto(h.getId(), h.getName(), h.getRegistrationNumber());
    }

    /** Active Horse Owners, chosen when registering a horse (PERM-003). */
    private List<UserAccount> horsesOwners() {
        access.require(Permission.MANAGE_HORSES);
        return accountRepository.findByRoleOrderByDisplayNameAsc(Role.HORSE_OWNER).stream()
                .filter(UserAccount::isActive).toList();
    }

    /** Active Head Trainers, chosen as the assigned trainer of a horse (PERM-003, PERM-045). */
    private List<UserAccount> headTrainers() {
        access.require(Permission.MANAGE_HORSES);
        return accountRepository.findByRoleOrderByDisplayNameAsc(Role.HEAD_TRAINER).stream()
                .filter(UserAccount::isActive).toList();
    }

    /** Active staff (everyone but Owners), for the performer of a care record (PERM-022). */
    private List<UserAccount> staffAccounts() {
        access.require(Permission.MANAGE_CARE);
        return accountRepository.findAllByOrderByDisplayNameAsc().stream()
                .filter(UserAccount::isActive).filter(a -> a.getRole() != Role.HORSE_OWNER).toList();
    }

    private static List<OptionDto> accountOptions(List<UserAccount> list) {
        Function<UserAccount, OptionDto> map = a -> new OptionDto(a.getId(), a.getDisplayName(), a.getEmail());
        return list.stream().map(map).toList();
    }
}
