package vn.racehorse.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.billing.entity.Invoice;
import vn.racehorse.billing.repository.InvoiceRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.payment.config.PaymentProperties;
import vn.racehorse.payment.dto.PaymentView;
import vn.racehorse.payment.dto.SepayNotice;
import vn.racehorse.payment.dto.TransferInfo;
import vn.racehorse.payment.dto.TransferRow;
import vn.racehorse.payment.dto.UnpaidInvoice;
import vn.racehorse.payment.entity.BankTransfer;
import vn.racehorse.payment.entity.InvoicePayment;
import vn.racehorse.payment.entity.InvoicePaymentCode;
import vn.racehorse.payment.repository.BankTransferRepository;
import vn.racehorse.payment.repository.InvoiceLockRepository;
import vn.racehorse.payment.repository.InvoicePaymentCodeRepository;
import vn.racehorse.payment.repository.InvoicePaymentRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Payment of a closed invoice by one bank transfer (BR-218 to BR-226, DEC-073). The invoice is only read: a payment
 * is a row of its own (BR-224). One transfer counts once (BR-220) and every accepted notice ends as an applied payment
 * or as an unmatched transfer with a reason (BR-221, TECH-233).
 */
@Service
public class PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);
    private static final String PROVIDER = "SEPAY";
    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** The code is followed by no digit; a prefix of the QR service ("SEVQR") may stand right before it. */
    private static final Pattern CODE = Pattern.compile("RH(\\d{8})(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final Map<String, String> REASON_TEXT = Map.of(
            BankTransfer.NO_CODE, "không có mã thanh toán",
            BankTransfer.UNKNOWN_CODE, "mã thanh toán không tồn tại",
            BankTransfer.AMOUNT_MISMATCH, "số tiền khác tổng",
            BankTransfer.ALREADY_PAID, "hóa đơn đã thanh toán");

    private final InvoiceRepository invoices;
    private final InvoiceLockRepository locks;
    private final InvoicePaymentCodeRepository codes;
    private final InvoicePaymentRepository payments;
    private final BankTransferRepository transfers;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final PaymentProperties props;

    public PaymentService(InvoiceRepository invoices, InvoiceLockRepository locks, InvoicePaymentCodeRepository codes,
                          InvoicePaymentRepository payments, BankTransferRepository transfers,
                          UserAccountRepository accounts, AccessGuard access, AuditService audit, ClubClock clock,
                          PaymentProperties props) {
        this.invoices = invoices;
        this.locks = locks;
        this.codes = codes;
        this.payments = payments;
        this.transfers = transfers;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.props = props;
    }

    // ---- the owner's payment ----------------------------------------------------------------------------------

    /** Opens the payment of an own invoice; makes the code the first time (BR-219). */
    @Transactional
    public PaymentView open(Long invoiceId) {
        AccountPrincipal user = access.require(Permission.PAY_INVOICE);
        Invoice invoice = ownInvoice(user, invoiceId);
        // The invoice row is locked, so two opens of one invoice never make two codes.
        locks.findByIdForUpdate(invoiceId);
        return view(invoice, true);
    }

    /** The status of an own invoice, without making a code; the page asks for it while it waits for the money. */
    @Transactional(readOnly = true)
    public PaymentView status(Long invoiceId) {
        AccountPrincipal user = access.require(Permission.PAY_INVOICE);
        return view(ownInvoice(user, invoiceId), false);
    }

    private Invoice ownInvoice(AccountPrincipal user, Long invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("hóa đơn"));
        if (!Objects.equals(invoice.getOwnerId(), user.id())) {
            throw ApiException.forbidden();
        }
        return invoice;
    }

    private PaymentView view(Invoice invoice, boolean create) {
        Optional<InvoicePayment> paid = payments.findById(invoice.getId());
        Optional<InvoicePaymentCode> code = codes.findById(invoice.getId());
        if (code.isEmpty() && create && paid.isEmpty()) {
            code = Optional.of(codes.save(new InvoicePaymentCode(invoice.getId(), newCode(), clock.now())));
        }
        String text = code.map(InvoicePaymentCode::getCode).orElse(null);
        if (paid.isPresent()) {
            boolean simulated = transfers.findById(paid.get().getTransferId()).map(BankTransfer::isSimulated)
                    .orElse(props.simulated());
            return new PaymentView(invoice.getId(), invoice.getNumber(), invoice.getMonth(), invoice.getTotal(), "PAID",
                    text, paid.get().getPaidAt(), simulated, null, null);
        }
        if (text == null) {
            return new PaymentView(invoice.getId(), invoice.getNumber(), invoice.getMonth(), invoice.getTotal(),
                    "UNPAID", null, null, props.simulated(), null, null);
        }
        String content = "SEVQR " + text;
        BigDecimal amount = invoice.getTotal().setScale(0, RoundingMode.HALF_UP);
        TransferInfo info = new TransferInfo(props.bankCode(), props.accountNumber(), props.accountName(), amount,
                content);
        return new PaymentView(invoice.getId(), invoice.getNumber(), invoice.getMonth(), invoice.getTotal(), "UNPAID",
                text, null, props.simulated(), info, qrUrl(amount, content));
    }

    private String qrUrl(BigDecimal amount, String content) {
        return props.qrBaseUrlOrDefault() + "?bank=" + enc(props.bankCode()) + "&acc=" + enc(props.accountNumber())
                + "&amount=" + amount.toPlainString() + "&des=" + enc(content) + "&template=qronly";
    }

    private static String enc(String text) {
        return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String newCode() {
        for (int attempt = 0; attempt < 8; attempt++) {
            String code = "RH" + String.format("%08d", RANDOM.nextInt(100_000_000));
            if (codes.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not make a free payment code");
    }

    // ---- receiving a transfer ---------------------------------------------------------------------------------

    /** True when the key of the notice is the agreed one; an unset key refuses everything (TECH-234). */
    public boolean keyMatches(String presented) {
        if (!props.keyConfigured() || presented == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8),
                props.webhookApiKey().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Records an announced transfer and applies it when it carries the code of an unpaid invoice and equals its
     * total (BR-220, BR-221). Announcing the same transfer again changes nothing.
     */
    @Transactional
    public void receive(SepayNotice notice) {
        if (!"in".equalsIgnoreCase(notice.transferType() == null ? "" : notice.transferType().strip())) {
            return; // money out is not a payment
        }
        Instant now = clock.now();
        int inserted = transfers.insertOnce(PROVIDER, notice.id().strip(), notice.transferAmount(),
                nullToEmpty(notice.content()), nullToEmpty(notice.accountNumber()), nullToEmpty(notice.referenceCode()),
                parseTime(notice.transactionDate()), now, props.simulated());
        if (inserted == 0) {
            LOG.info("Transfer {} was announced before; nothing changes", notice.id());
            return;
        }
        BankTransfer transfer = transfers.findByProviderAndProviderId(PROVIDER, notice.id().strip()).orElseThrow();
        List<String> found = codesIn(nullToEmpty(notice.content()) + " " + nullToEmpty(notice.code()));
        if (found.isEmpty()) {
            transfer.keepUnmatched(BankTransfer.NO_CODE);
            return;
        }
        Optional<InvoicePaymentCode> known = found.stream().map(codes::findByCode).flatMap(Optional::stream).findFirst();
        if (known.isEmpty()) {
            transfer.keepUnmatched(BankTransfer.UNKNOWN_CODE);
            return;
        }
        Invoice invoice = locks.findByIdForUpdate(known.get().getInvoiceId()).orElseThrow();
        if (payments.existsById(invoice.getId())) {
            transfer.keepUnmatched(BankTransfer.ALREADY_PAID);
        } else if (invoice.getTotal().compareTo(transfer.getAmount()) != 0) {
            transfer.keepUnmatched(BankTransfer.AMOUNT_MISMATCH);
        } else {
            payments.saveAndFlush(new InvoicePayment(invoice.getId(), transfer.getId(), now, InvoicePayment.TRANSFER));
            transfer.apply(invoice.getId(), BankTransfer.APPLIED);
            LOG.info("Invoice {} paid by transfer {}", invoice.getNumber(), notice.id());
        }
    }

    static List<String> codesIn(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = CODE.matcher(text);
        while (m.find()) {
            out.add("RH" + m.group(1));
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Instant parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.strip().replace(' ', 'T')).atZone(BANK_ZONE).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- the Club Manager ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TransferRow> received() {
        access.require(Permission.MANAGE_PAYMENTS);
        return rows(transfers.findByStatusInOrderByReceivedAtDescIdDesc(
                List.of(BankTransfer.APPLIED, BankTransfer.ASSIGNED)));
    }

    /** Every transfer that was not applied by itself, still open or assigned since (BR-221, BR-222). */
    @Transactional(readOnly = true)
    public List<TransferRow> unmatched() {
        access.require(Permission.MANAGE_PAYMENTS);
        return rows(transfers.findByStatusInOrderByReceivedAtDescIdDesc(
                List.of(BankTransfer.UNMATCHED, BankTransfer.ASSIGNED))).stream()
                .filter(r -> r.reason() != null).toList();
    }

    @Transactional(readOnly = true)
    public List<UnpaidInvoice> unpaidInvoices() {
        access.require(Permission.MANAGE_PAYMENTS);
        List<UnpaidInvoice> out = new ArrayList<>();
        for (Invoice i : invoices.findAll()) {
            if (!payments.existsById(i.getId())) {
                out.add(new UnpaidInvoice(i.getId(), i.getNumber(), i.getMonth(), i.getTotal(), i.getOwnerId(),
                        ownerName(i.getOwnerId())));
            }
        }
        out.sort(java.util.Comparator.comparing(UnpaidInvoice::month).reversed().thenComparing(UnpaidInvoice::number));
        return out;
    }

    private List<TransferRow> rows(List<BankTransfer> list) {
        List<TransferRow> out = new ArrayList<>();
        for (BankTransfer t : list) {
            Invoice invoice = t.getInvoiceId() == null ? null : invoices.findById(t.getInvoiceId()).orElse(null);
            out.add(new TransferRow(t.getId(), t.getAmount(), t.getContent(), t.getTransferredAt(), t.getReceivedAt(),
                    t.isSimulated(), t.getStatus(), t.getReason(),
                    t.getReason() == null ? null : REASON_TEXT.get(t.getReason()),
                    t.getInvoiceId(), invoice == null ? null : invoice.getNumber(),
                    invoice == null ? null : ownerName(invoice.getOwnerId())));
        }
        return out;
    }

    private String ownerName(Long ownerId) {
        return accounts.findById(ownerId).map(UserAccount::getDisplayName).orElse("?");
    }

    /** The Club Manager decides which invoice an unmatched transfer pays, whatever the amount (BR-222, TECH-238). */
    @Transactional
    public void assign(Long transferId, Long invoiceId, String reason) {
        AccountPrincipal actor = access.require(Permission.MANAGE_PAYMENTS);
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty()) {
            throw ApiException.invalid("REASON_REQUIRED", "reason", "Bắt buộc nhập lý do.");
        }
        BankTransfer transfer = transfers.findByIdForUpdate(transferId)
                .orElseThrow(() -> ApiException.notFound("khoản chuyển tiền"));
        if (!BankTransfer.UNMATCHED.equals(transfer.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFER_NOT_OPEN",
                    "Khoản chuyển tiền này đã được áp dụng hoặc đã được gán.");
        }
        Invoice invoice = locks.findByIdForUpdate(invoiceId).orElseThrow(() -> ApiException.notFound("hóa đơn"));
        if (payments.existsById(invoice.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVOICE_ALREADY_PAID", "Hóa đơn này đã thanh toán.");
        }
        payments.saveAndFlush(new InvoicePayment(invoice.getId(), transfer.getId(), clock.now(),
                InvoicePayment.ASSIGNED));
        transfer.apply(invoice.getId(), BankTransfer.ASSIGNED);
        audit.record(actor, "TRANSFER_ASSIGNED", "INVOICE", invoice.getId(), Map.of(
                "transferId", transfer.getId(), "invoice", invoice.getNumber(),
                "amount", transfer.getAmount().toPlainString(), "reason", why));
    }
}
