package vn.racehorse.billing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.billing.dto.Statement;
import vn.racehorse.billing.dto.StatementCategoryGroup;
import vn.racehorse.billing.dto.StatementCategoryTotal;
import vn.racehorse.billing.dto.StatementHorseGroup;
import vn.racehorse.billing.dto.StatementLine;
import vn.racehorse.billing.dto.StatementOwnerRow;
import vn.racehorse.billing.entity.Charge;
import vn.racehorse.billing.entity.Invoice;
import vn.racehorse.billing.entity.InvoiceLine;
import vn.racehorse.billing.repository.BillingMonthRepository;
import vn.racehorse.billing.repository.ChargeRepository;
import vn.racehorse.billing.repository.InvoiceLineRepository;
import vn.racehorse.billing.repository.InvoiceRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Statements of the running month (live from the charges) and of closed months (the invoice), grouped by horse then
 * category (BR-180, BR-182, TECH-170). The client never sums; it shows these totals.
 */
@Service
public class StatementService {

    private final ChargeRepository charges;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final BillingMonthRepository months;
    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final ClubClock clock;

    public StatementService(ChargeRepository charges, InvoiceRepository invoices, InvoiceLineRepository invoiceLines,
                            BillingMonthRepository months, HorseRepository horses, UserAccountRepository accounts,
                            AccessGuard access, ClubClock clock) {
        this.charges = charges;
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.months = months;
        this.horses = horses;
        this.accounts = accounts;
        this.access = access;
        this.clock = clock;
    }

    /** The signed-in owner's own statement. */
    @Transactional(readOnly = true)
    public Statement mine(String month) {
        AccountPrincipal user = access.require(Permission.VIEW_BILLING);
        return build(parse(month), user.id(), name(user.id()));
    }

    /** One owner's statement for the Club Manager; an owner may only ask for their own. */
    @Transactional(readOnly = true)
    public Statement of(Long ownerId, String month) {
        AccountPrincipal user = access.require(Permission.VIEW_BILLING);
        if (user.role() == Role.HORSE_OWNER && !Objects.equals(user.id(), ownerId)) {
            throw ApiException.forbidden();
        }
        accounts.findById(ownerId).orElseThrow(() -> ApiException.notFound("chủ ngựa"));
        return build(parse(month), ownerId, name(ownerId));
    }

    /** One row per owner with charges in the month, for the Club Manager. */
    @Transactional(readOnly = true)
    public List<StatementOwnerRow> owners(String month) {
        AccountPrincipal user = access.require(Permission.VIEW_BILLING);
        if (user.role() != Role.CLUB_MANAGER) {
            throw ApiException.forbidden();
        }
        YearMonth ym = parse(month);
        boolean closed = months.existsById(ym.toString());
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        for (Charge c : charges.allInMonth(ym.atDay(1), ym.atEndOfMonth())) {
            totals.merge(c.getOwnerId(), c.getAmount(), BigDecimal::add);
        }
        List<StatementOwnerRow> rows = new ArrayList<>();
        totals.forEach((ownerId, total) -> {
            Invoice invoice = closed ? invoices.findByOwnerIdAndMonth(ownerId, ym.toString()).orElse(null) : null;
            rows.add(new StatementOwnerRow(ownerId, name(ownerId), total, closed, invoice == null ? null : invoice.getId()));
        });
        rows.sort((a, b) -> a.ownerName().compareToIgnoreCase(b.ownerName()));
        return rows;
    }

    /** The cost panel of one horse: its owner (also a former one, for their period) and the Club Manager. */
    @Transactional(readOnly = true)
    public Statement ofHorse(Long horseId, String month) {
        AccountPrincipal user = access.require(Permission.VIEW_BILLING);
        Horse horse = horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        YearMonth ym = parse(month);
        List<Charge> list;
        Long ownerId = null;
        String ownerName = null;
        if (user.role() == Role.HORSE_OWNER) {
            boolean related = Objects.equals(horse.getOwner().getId(), user.id())
                    || charges.existsByHorseIdAndOwnerId(horseId, user.id());
            if (!related) {
                throw ApiException.forbidden();
            }
            list = charges.forHorseAndOwnerInMonth(horseId, user.id(), ym.atDay(1), ym.atEndOfMonth());
            ownerId = user.id();
            ownerName = name(user.id());
        } else {
            list = charges.forHorseInMonth(horseId, ym.atDay(1), ym.atEndOfMonth());
        }
        List<StatementHorseGroup> groups = group(list);
        return new Statement(ym.toString(), months.existsById(ym.toString()), ownerId, ownerName, sum(list),
                groups, categoryTotals(groups), null, null, null, throughYesterday());
    }

    private Statement build(YearMonth ym, Long ownerId, String ownerName) {
        boolean closed = months.existsById(ym.toString());
        if (closed) {
            Invoice invoice = invoices.findByOwnerIdAndMonth(ownerId, ym.toString()).orElse(null);
            if (invoice == null) {
                return new Statement(ym.toString(), true, ownerId, ownerName, BigDecimal.ZERO.setScale(2), List.of(),
                        List.of(), null, null, null, throughYesterday());
            }
            return fromInvoice(invoice, ownerName);
        }
        List<Charge> list = charges.forOwnerInMonth(ownerId, ym.atDay(1), ym.atEndOfMonth());
        List<StatementHorseGroup> groups = group(list);
        return new Statement(ym.toString(), false, ownerId, ownerName, sum(list), groups, categoryTotals(groups), null,
                null, null, throughYesterday());
    }

    /** A closed invoice read back as a statement; its lines were copied at closing. */
    Statement fromInvoice(Invoice invoice, String ownerName) {
        Map<String, Map<Charge.Category, List<StatementLine>>> byHorse = new LinkedHashMap<>();
        Map<String, Long> horseIds = new LinkedHashMap<>();
        for (InvoiceLine l : invoiceLines.findByInvoiceIdOrderByIdAsc(invoice.getId())) {
            String key = l.getHorseName() == null ? "" : l.getHorseName() + "#" + l.getHorseId();
            horseIds.putIfAbsent(key, l.getHorseId());
            byHorse.computeIfAbsent(key, k -> new EnumMap<>(Charge.Category.class))
                    .computeIfAbsent(Charge.Category.valueOf(l.getCategory()), k -> new ArrayList<>())
                    .add(new StatementLine(l.getWorkDate(), l.getDescription(), l.getQuantity(), l.getUnitPrice(),
                            l.getAmount(), l.getReason()));
        }
        List<StatementHorseGroup> groups = new ArrayList<>();
        byHorse.forEach((key, cats) -> groups.add(horseGroup(horseIds.get(key),
                key.isEmpty() ? "Không gắn ngựa" : key.substring(0, key.lastIndexOf('#')), cats)));
        return new Statement(invoice.getMonth(), true, invoice.getOwnerId(), ownerName, invoice.getTotal(), groups,
                categoryTotals(groups), invoice.getId(), invoice.getNumber(), invoice.getClosedAt(), throughYesterday());
    }

    private List<StatementHorseGroup> group(List<Charge> list) {
        Map<Long, Map<Charge.Category, List<StatementLine>>> byHorse = new LinkedHashMap<>();
        for (Charge c : list) {
            byHorse.computeIfAbsent(c.getHorseId() == null ? 0L : c.getHorseId(), k -> new EnumMap<>(Charge.Category.class))
                    .computeIfAbsent(c.getCategory(), k -> new ArrayList<>())
                    .add(new StatementLine(c.getWorkDate(), c.getDescription(), c.getQuantity(), c.getUnitPrice(),
                            c.getAmount(), c.getReason()));
        }
        Map<Long, Horse> known = new LinkedHashMap<>();
        horses.findAllById(byHorse.keySet().stream().filter(id -> id != 0L).toList()).forEach(h -> known.put(h.getId(), h));
        List<StatementHorseGroup> groups = new ArrayList<>();
        byHorse.forEach((id, cats) -> {
            Horse horse = known.get(id);
            groups.add(horseGroup(id == 0L ? null : id, horse == null ? "Không gắn ngựa" : horse.getName(), cats));
        });
        groups.sort((a, b) -> a.horseName().compareToIgnoreCase(b.horseName()));
        return groups;
    }

    private static StatementHorseGroup horseGroup(Long horseId, String horseName, Map<Charge.Category, List<StatementLine>> cats) {
        List<StatementCategoryGroup> groups = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Charge.Category category : Charge.Category.values()) {
            List<StatementLine> lines = cats.get(category);
            if (lines == null) {
                continue;
            }
            BigDecimal t = lines.stream().map(StatementLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            groups.add(new StatementCategoryGroup(category.name(), t, lines));
            total = total.add(t);
        }
        return new StatementHorseGroup(horseId, horseName, total, groups);
    }

    /** The category totals across horses, from the group totals the service already computed (BR-203). */
    private static List<StatementCategoryTotal> categoryTotals(List<StatementHorseGroup> groups) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Charge.Category category : Charge.Category.values()) {
            for (StatementHorseGroup horse : groups) {
                for (StatementCategoryGroup c : horse.categories()) {
                    if (c.category().equals(category.name())) {
                        totals.merge(category.name(), c.total(), BigDecimal::add);
                    }
                }
            }
        }
        List<StatementCategoryTotal> out = new ArrayList<>();
        totals.forEach((category, total) -> out.add(new StatementCategoryTotal(category, total)));
        return out;
    }

    private static BigDecimal sum(List<Charge> list) {
        return list.stream().map(Charge::getAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private LocalDate throughYesterday() {
        return clock.today().minusDays(1);
    }

    private String name(Long accountId) {
        return accounts.findById(accountId).map(UserAccount::getDisplayName).orElse("?");
    }

    /** The month of a request, the current club month when absent. */
    YearMonth parse(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(clock.today());
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.invalid("VALIDATION_FAILED", "month", "Tháng phải có dạng yyyy-MM.");
        }
    }
}
