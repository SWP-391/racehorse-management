package vn.racehorse.billing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.billing.dto.ExportFile;
import vn.racehorse.billing.dto.Statement;
import vn.racehorse.billing.dto.StatementOwnerRow;
import vn.racehorse.billing.repository.InvoiceRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Statements and invoices as Excel files (BR-204, PERM-057). Every file is built from the statement the screen reads
 * through the same service call, so the permission checks of the screen apply unchanged and the numbers cannot differ.
 */
@Service
public class StatementExportService {

    private static final String XLSX = ".xlsx";

    private final StatementService statements;
    private final InvoiceService invoices;
    private final InvoiceRepository invoiceRepository;
    private final AccessGuard access;
    private final ClubClock clock;

    public StatementExportService(StatementService statements, InvoiceService invoices,
                                  InvoiceRepository invoiceRepository, AccessGuard access, ClubClock clock) {
        this.statements = statements;
        this.invoices = invoices;
        this.invoiceRepository = invoiceRepository;
        this.access = access;
        this.clock = clock;
    }

    /** Every owner of a month, for the Club Manager. */
    @Transactional(readOnly = true)
    public ExportFile allOwners(String month) {
        AccountPrincipal user = access.require(Permission.EXPORT_BILLING);
        if (user.role() != Role.CLUB_MANAGER) {
            throw ApiException.forbidden();
        }
        String key = statements.parse(month).toString();
        List<StatementWorkbook.Part> parts = new ArrayList<>();
        for (StatementOwnerRow row : statements.owners(key)) {
            parts.add(part(statements.of(row.ownerId(), key)));
        }
        return file("Bảng kê chi phí tháng " + key + " (tất cả chủ ngựa)", parts, "bang-ke-" + key.replace("-", "") + "-tat-ca");
    }

    /** One owner's month: the Club Manager for any owner, a Horse Owner for themselves only. */
    @Transactional(readOnly = true)
    public ExportFile ownerMonth(Long ownerId, String month) {
        access.require(Permission.EXPORT_BILLING);
        return single(statements.of(ownerId, month));
    }

    @Transactional(readOnly = true)
    public ExportFile mine(String month) {
        access.require(Permission.EXPORT_BILLING);
        return single(statements.mine(month));
    }

    /** A closed invoice: the same lines and total as when it was closed, whatever was renamed since (BR-180). */
    @Transactional(readOnly = true)
    public ExportFile invoice(Long id) {
        access.require(Permission.EXPORT_BILLING);
        return single(invoices.invoice(id));
    }

    private ExportFile single(Statement s) {
        String title = s.closed() && s.invoiceNumber() != null
                ? "Hóa đơn " + s.invoiceNumber() + " - " + s.ownerName() + " - tháng " + s.month()
                : "Bảng kê chi phí tháng " + s.month() + " - " + s.ownerName() + " (" + StatementWorkbook.PROVISIONAL.toLowerCase() + ")";
        String name = s.closed() && s.invoiceNumber() != null
                ? "hoa-don-" + s.invoiceNumber()
                : "bang-ke-" + (s.closed() ? "" : "tam-tinh-") + s.month().replace("-", "") + "-" + s.ownerId();
        return file(title, List.of(part(s)), name);
    }

    private StatementWorkbook.Part part(Statement s) {
        Instant closedAt = s.invoiceId() == null ? null
                : invoiceRepository.findById(s.invoiceId()).map(i -> i.getClosedAt()).orElse(null);
        return new StatementWorkbook.Part(s, closedAt);
    }

    private ExportFile file(String title, List<StatementWorkbook.Part> parts, String name) {
        return new ExportFile(name + XLSX, StatementWorkbook.write(title, parts, clock.zone()));
    }
}
