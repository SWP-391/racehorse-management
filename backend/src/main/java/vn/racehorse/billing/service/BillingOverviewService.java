package vn.racehorse.billing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.billing.dto.BillingMonthTotal;
import vn.racehorse.billing.dto.BillingOverview;
import vn.racehorse.billing.dto.BillingOwnerTotal;
import vn.racehorse.billing.dto.StatementCategoryTotal;
import vn.racehorse.billing.entity.Charge;
import vn.racehorse.billing.repository.ChargeRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The totals behind the charts of the billing overview: by month, by category and by owner (BR-180, BR-209). */
@Service
public class BillingOverviewService {

    static final int WINDOW_MONTHS = 12;
    static final int ALL_MONTHS_MAX = 24;
    static final int TOP_OWNERS = 8;

    private final ChargeRepository charges;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final ClubClock clock;

    public BillingOverviewService(ChargeRepository charges, UserAccountRepository accounts, AccessGuard access, ClubClock clock) {
        this.charges = charges;
        this.accounts = accounts;
        this.access = access;
        this.clock = clock;
    }

    /** `month` is `yyyy-MM`, `all`, or absent for the current club month. Club Manager only, like the owners' list. */
    @Transactional(readOnly = true)
    public BillingOverview overview(String month) {
        AccountPrincipal user = access.require(Permission.VIEW_BILLING);
        if (user.role() != Role.CLUB_MANAGER) {
            throw ApiException.forbidden();
        }
        boolean all = "all".equalsIgnoreCase(month);
        YearMonth chosen = all ? null : parse(month);

        YearMonth last = all ? YearMonth.from(clock.today()) : chosen;
        YearMonth first = all ? earliest(last) : chosen.minusMonths(WINDOW_MONTHS - 1);
        List<Charge> inWindow = charges.allInMonth(first.atDay(1), last.atEndOfMonth());

        Map<YearMonth, BigDecimal> byMonth = new TreeMap<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            byMonth.put(m, BigDecimal.ZERO);
        }
        Map<Charge.Category, BigDecimal> byCategory = new EnumMap<>(Charge.Category.class);
        Map<Long, BigDecimal> byOwner = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Charge c : inWindow) {
            YearMonth m = YearMonth.from(c.getChargeDate());
            byMonth.merge(m, c.getAmount(), BigDecimal::add);
            if (all || m.equals(chosen)) {
                total = total.add(c.getAmount());
                byCategory.merge(c.getCategory(), c.getAmount(), BigDecimal::add);
                byOwner.merge(c.getOwnerId(), c.getAmount(), BigDecimal::add);
            }
        }

        List<BillingMonthTotal> every = new ArrayList<>();
        byMonth.forEach((m, t) -> every.add(new BillingMonthTotal(m.toString(), t)));
        List<BillingMonthTotal> months = every.size() > ALL_MONTHS_MAX
                ? new ArrayList<>(every.subList(every.size() - ALL_MONTHS_MAX, every.size())) : every;
        List<StatementCategoryTotal> categories = new ArrayList<>();
        byCategory.forEach((c, t) -> categories.add(new StatementCategoryTotal(c.name(), t)));
        categories.sort(Comparator.comparing(StatementCategoryTotal::total).reversed());
        List<BillingOwnerTotal> owners = byOwner.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(TOP_OWNERS)
                .map(e -> new BillingOwnerTotal(e.getKey(), name(e.getKey()), e.getValue())).toList();
        return new BillingOverview(all ? "all" : chosen.toString(), total, byOwner.size(), months, categories, owners);
    }

    /** The first month with a charge (so "all" starts there), or the current month when nothing was charged yet. */
    private YearMonth earliest(YearMonth current) {
        return charges.earliestChargeDate().map(YearMonth::from).filter(m -> !m.isAfter(current)).orElse(current);
    }

    private String name(Long ownerId) {
        return accounts.findById(ownerId).map(UserAccount::getDisplayName).orElse("?");
    }

    private YearMonth parse(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(clock.today());
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.invalid("VALIDATION_FAILED", "month", "Tháng phải có dạng yyyy-MM hoặc all.");
        }
    }
}
