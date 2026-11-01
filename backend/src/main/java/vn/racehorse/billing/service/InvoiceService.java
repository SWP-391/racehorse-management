package vn.racehorse.billing.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.billing.dto.ChargeSpec;
import vn.racehorse.billing.dto.ClosingState;
import vn.racehorse.billing.dto.ClosingStatusResponse;
import vn.racehorse.billing.dto.InvoiceCloseResult;
import vn.racehorse.billing.dto.InvoiceView;
import vn.racehorse.billing.dto.Statement;
import vn.racehorse.billing.entity.Charge;
import vn.racehorse.billing.entity.Invoice;
import vn.racehorse.billing.entity.InvoiceLine;
import vn.racehorse.billing.repository.BillingDayRunRepository;
import vn.racehorse.billing.repository.BillingMonthRepository;
import vn.racehorse.billing.repository.ChargeRepository;
import vn.racehorse.billing.repository.InvoiceLineRepository;
import vn.racehorse.billing.repository.InvoiceRepository;
import vn.racehorse.billing.repository.ServicePriceRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Month closing, immutable invoices and correction lines (BR-180, TECH-171). */
@Service
public class InvoiceService {

    private final ChargeRepository charges;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final BillingMonthRepository months;
    private final BillingDayRunRepository runs;
    private final ServicePriceRepository prices;
    private final ChargeService chargeService;
    private final StatementService statements;
    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;

    public InvoiceService(ChargeRepository charges, InvoiceRepository invoices, InvoiceLineRepository invoiceLines,
                          BillingMonthRepository months, BillingDayRunRepository runs, ServicePriceRepository prices,
                          ChargeService chargeService, StatementService statements, HorseRepository horses,
                          UserAccountRepository accounts, JdbcTemplate jdbc, AccessGuard access, AuditService audit,
                          ClubClock clock) {
        this.charges = charges;
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.months = months;
        this.runs = runs;
        this.prices = prices;
        this.chargeService = chargeService;
        this.statements = statements;
        this.horses = horses;
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    /** Whether a month can be closed now and, if not, why: the same answer the closing gives (BR-205). */
    @Transactional(readOnly = true)
    public ClosingStatusResponse closingStatus(String month) {
        access.require(Permission.SET_PRICES_AND_CLOSE_MONTH);
        return check(statements.parse(month));
    }

    private ClosingStatusResponse check(YearMonth ym) {
        String key = ym.toString();
        if (months.existsById(key)) {
            return refused(key, ClosingState.CLOSED, null, "Tháng " + key + " đã được chốt.");
        }
        if (!YearMonth.from(clock.today()).isAfter(ym)) {
            return refused(key, ClosingState.NOT_FINISHED, null, "Tháng " + key + " chưa kết thúc, chưa thể chốt.");
        }
        LocalDate last = ym.atEndOfMonth();
        LocalDate firstFee = prices.firstDailyFeeDate().orElse(null);
        boolean feeExpected = firstFee != null && !firstFee.isAfter(last);
        if (feeExpected && runs.lastDay().map(d -> d.isBefore(last)).orElse(true)) {
            return refused(key, ClosingState.FEES_PENDING, null,
                    "Phí chuồng của tháng " + key + " chưa được tính đủ đến ngày cuối tháng; thử lại sau.");
        }
        return charges.earliestOpenBefore(ym.atDay(1))
                .map(d -> refused(key, ClosingState.PREVIOUS_MONTH_OPEN, YearMonth.from(d).toString(),
                        "Tháng " + YearMonth.from(d) + " vẫn còn khoản chưa chốt; hãy chốt tháng đó trước."))
                .orElseGet(() -> new ClosingStatusResponse(key, ClosingState.READY, null, null));
    }

    private static ClosingStatusResponse refused(String month, ClosingState state, String openMonth, String message) {
        return new ClosingStatusResponse(month, state, openMonth, message);
    }

    /** Closes a finished month: one invoice per owner with charges, lines copied, never changed afterwards. */
    @Transactional
    public InvoiceCloseResult close(String month) {
        AccountPrincipal actor = access.require(Permission.SET_PRICES_AND_CLOSE_MONTH);
        YearMonth ym = statements.parse(month);
        String key = ym.toString();
        ClosingStatusResponse status = check(ym);
        if (status.state() != ClosingState.READY) {
            throw new ApiException(HttpStatus.CONFLICT, status.state().refusalCode(), status.message());
        }
        LocalDate last = ym.atEndOfMonth();
        // The primary key makes one request the winner when two close the same month at once.
        int won = jdbc.update("INSERT INTO billing_month (month, closed_at, closed_by) VALUES (?, ?, ?) "
                + "ON CONFLICT (month) DO NOTHING", key, Timestamp.from(clock.now()), actor.id());
        if (won == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "MONTH_ALREADY_CLOSED", "Tháng " + key + " đã được chốt.");
        }
        Map<Long, List<Charge>> byOwner = new LinkedHashMap<>();
        for (Charge c : charges.allInMonth(ym.atDay(1), last)) {
            if (c.getInvoiceId() == null) {
                byOwner.computeIfAbsent(c.getOwnerId(), k -> new ArrayList<>()).add(c);
            }
        }
        BigDecimal grand = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<Long, List<Charge>> e : byOwner.entrySet()) {
            BigDecimal total = e.getValue().stream().map(Charge::getAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            String number = key.replace("-", "") + "-" + String.format("%04d", e.getKey());
            Invoice invoice = invoices.save(new Invoice(e.getKey(), key, number, total, actor.id(), clock.now()));
            for (Charge c : e.getValue()) {
                String horseName = c.getHorseId() == null ? null
                        : horses.findById(c.getHorseId()).map(h -> h.getName()).orElse(null);
                invoiceLines.save(new InvoiceLine(invoice.getId(), c, horseName));
                c.attach(invoice.getId());
            }
            grand = grand.add(total);
        }
        audit.record(actor, "BILLING_MONTH_CLOSED", "BILLING_MONTH", null,
                Map.of("month", key, "invoices", byOwner.size(), "total", grand.toPlainString()));
        return new InvoiceCloseResult(key, byOwner.size(), grand);
    }

    /** A signed correction line in a month that is not closed; the reason is mandatory (BR-180). */
    @Transactional
    public void adjust(Long ownerId, Long horseId, LocalDate date, BigDecimal amount, String reason) {
        AccountPrincipal actor = access.require(Permission.SET_PRICES_AND_CLOSE_MONTH);
        Map<String, String> errors = new LinkedHashMap<>();
        if (ownerId == null) errors.put("ownerId", "Chọn chủ ngựa.");
        if (amount == null || amount.signum() == 0 || amount.stripTrailingZeros().scale() > 0) {
            errors.put("amount", "Số tiền phải là số đồng nguyên khác 0 (âm là giảm trừ).");
        }
        if (reason == null || reason.isBlank()) errors.put("reason", "Bắt buộc nhập lý do.");
        if (date == null) errors.put("date", "Bắt buộc nhập ngày.");
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thông tin điều chỉnh chưa hợp lệ.", errors);
        }
        UserAccount owner = accounts.findById(ownerId).filter(a -> a.getRole() == Role.HORSE_OWNER)
                .orElseThrow(() -> ApiException.invalid("INVALID_OWNER", "ownerId", "Chọn một chủ ngựa."));
        if (horseId != null) {
            horses.findById(horseId).orElseThrow(() -> ApiException.invalid("INVALID_HORSE", "horseId", "Không tìm thấy ngựa."));
        }
        if (months.existsById(YearMonth.from(date).toString())) {
            throw new ApiException(HttpStatus.CONFLICT, "MONTH_CLOSED",
                    "Tháng " + YearMonth.from(date) + " đã chốt, chọn ngày trong tháng chưa chốt.");
        }
        chargeService.record(new ChargeSpec(horseId, owner.getId(), Charge.Category.ADJUSTMENT, date,
                reason.trim(), BigDecimal.ONE, amount, amount, "ADJ:" + UUID.randomUUID(), null, reason.trim(),
                actor.id()));
        audit.record(actor, "BILLING_ADJUSTMENT_ADDED", "BILLING_MONTH", ownerId,
                Map.of("ownerId", ownerId, "date", date.toString(), "amount", amount.toPlainString(),
                        "reason", reason.trim()));
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> list(Long ownerId, String month) {
        AccountPrincipal user = access.require(Permission.VIEW_BILLING);
        Long scope = user.role() == Role.HORSE_OWNER ? user.id() : ownerId;
        List<Invoice> found;
        if (month != null && !month.isBlank()) {
            found = invoices.findByMonthOrderByNumberAsc(statements.parse(month).toString());
        } else if (scope != null) {
            found = invoices.findByOwnerIdOrderByMonthDesc(scope);
        } else {
            found = invoices.findAll();
        }
        return found.stream().filter(i -> scope == null || Objects.equals(i.getOwnerId(), scope))
                .map(i -> new InvoiceView(i.getId(), i.getOwnerId(), accounts.findById(i.getOwnerId())
                        .map(UserAccount::getDisplayName).orElse("?"), i.getMonth(), i.getNumber(), i.getTotal(),
                        i.getClosedAt())).toList();
    }

    @Transactional(readOnly = true)
    public Statement invoice(Long id) {
        AccountPrincipal user = access.require(Permission.VIEW_BILLING);
        Invoice invoice = invoices.findById(id).orElseThrow(() -> ApiException.notFound("hóa đơn"));
        if (user.role() == Role.HORSE_OWNER && !Objects.equals(invoice.getOwnerId(), user.id())) {
            throw ApiException.forbidden();
        }
        return statements.fromInvoice(invoice, accounts.findById(invoice.getOwnerId())
                .map(UserAccount::getDisplayName).orElse("?"));
    }
}
