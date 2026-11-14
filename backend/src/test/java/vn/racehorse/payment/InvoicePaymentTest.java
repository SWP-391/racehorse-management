package vn.racehorse.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The owner's payment and the receiving of a transfer (AC-469..AC-475, AC-479..AC-482). */
class InvoicePaymentTest extends PaymentTestBase {

    @Test
    void AC_469_qrForTheFullTotalWithAStableCode() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 1_500_000);
        JsonNode first = body(asJson(owner, post("/api/payments/invoices/" + invoice + "/open"), Map.of())
                .andExpect(status().isOk()));
        JsonNode second = body(asJson(owner, post("/api/payments/invoices/" + invoice + "/open"), Map.of())
                .andExpect(status().isOk()));

        assertThat(first.get("status").asText()).isEqualTo("UNPAID");
        assertThat(first.get("paymentCode").asText()).matches("RH\\d{8}");
        assertThat(second.get("paymentCode").asText()).isEqualTo(first.get("paymentCode").asText());
        assertThat(first.get("transfer").get("amount").decimalValue()).isEqualByComparingTo("1500000");
        assertThat(first.get("transfer").get("content").asText()).isEqualTo("SEVQR " + first.get("paymentCode").asText());
        String qr = first.get("qrUrl").asText();
        assertThat(qr).contains("amount=1500000").contains("des=SEVQR%20" + first.get("paymentCode").asText())
                .startsWith("https://vietqr.app/img?");
        assertThat(first.get("simulated").asBoolean()).isTrue();
    }

    @Test
    void paymentCodesAreUniquePerInvoice() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        String a = openPayment(owner, invoice(owner, 100_000));
        String b = openPayment(owner, invoice(owner, 100_000));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void concurrentOpensMakeOneCode() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 250_000);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> codes = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Callable<String> task = () -> openPayment(owner, invoice);
                codes.add(pool.submit(task));
            }
            String first = codes.get(0).get();
            for (Future<String> f : codes) {
                assertThat(f.get()).isEqualTo(first);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void AC_481_aMonthThatIsNotClosedOffersNoPayment() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        asJson(owner, post("/api/payments/invoices/999999999/open"), Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void AC_470_aMatchingTransferPaysTheInvoice() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 1_500_000);
        String code = openPayment(owner, invoice);
        JsonNode before = body(as(owner, get("/api/billing/invoices")));

        sendKeyed(notice(nextTransferId(), 1_500_000, "SEVQR" + code)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        JsonNode paid = paymentOf(owner, invoice);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("paidAt").isNull()).isFalse();
        assertThat(paid.has("qrUrl") && !paid.get("qrUrl").isNull()).isFalse();
        // the invoice itself is untouched (BR-224)
        JsonNode row = null;
        for (JsonNode i : body(as(owner, get("/api/billing/invoices")))) {
            if (i.get("id").asLong() == invoice) {
                row = i;
            }
        }
        assertThat(row.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(row.get("total").decimalValue()).isEqualByComparingTo("1500000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_line WHERE invoice_id = ?", Long.class, invoice))
                .isZero();
        assertThat(before).isNotNull();
    }

    @Test
    void AC_482_aPaidInvoiceShowsNoQr() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 80_000);
        String code = openPayment(owner, invoice);
        sendKeyed(notice(nextTransferId(), 80_000, "SEVQR " + code)).andExpect(status().isOk());
        asJson(owner, post("/api/payments/invoices/" + invoice + "/open"), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.qrUrl").doesNotExist());
    }

    @Test
    void theCodeIsFoundInAnyCaseInsideTheContent() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 90_000);
        String code = openPayment(owner, invoice);
        sendKeyed(notice(nextTransferId(), 90_000, "SEVQR " + code.toLowerCase() + " chuyen tien")).andExpect(status().isOk());
        assertThat(paymentOf(owner, invoice).get("status").asText()).isEqualTo("PAID");
    }

    @Test
    void AC_471_theSameTransferTwiceAndAtTheSameTimeCountsOnce() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 500_000);
        String code = openPayment(owner, invoice);
        String id = nextTransferId();
        Map<String, Object> body = notice(id, 500_000, "SEVQR " + code);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> sendKeyed(body).andReturn().getResponse().getStatus()));
            }
            for (Future<Integer> r : results) {
                assertThat(r.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        sendKeyed(body).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(countTransfers(id)).isEqualTo(1);
        assertThat(countPayments(invoice)).isEqualTo(1);
    }

    @Test
    void AC_472_AC_473_aWrongAmountStaysUnmatchedAndNothingIsRefunded() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal manager = manager();
        long invoice = invoice(owner, 1_500_000);
        String code = openPayment(owner, invoice);
        String less = nextTransferId();
        String more = nextTransferId();
        sendKeyed(notice(less, 1_000_000, "SEVQR " + code)).andExpect(status().isOk());
        sendKeyed(notice(more, 2_000_000, "SEVQR " + code)).andExpect(status().isOk());

        assertThat(paymentOf(owner, invoice).get("status").asText()).isEqualTo("UNPAID");
        assertThat(reasonOf(manager, less)).isEqualTo("số tiền khác tổng");
        assertThat(reasonOf(manager, more)).isEqualTo("số tiền khác tổng");
        assertThat(countPayments(invoice)).isZero();
    }

    @Test
    void twoPartTransfersAreNeverAddedTogether() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 1_000_000);
        String code = openPayment(owner, invoice);
        sendKeyed(notice(nextTransferId(), 400_000, "SEVQR " + code)).andExpect(status().isOk());
        sendKeyed(notice(nextTransferId(), 600_000, "SEVQR " + code)).andExpect(status().isOk());
        assertThat(paymentOf(owner, invoice).get("status").asText()).isEqualTo("UNPAID");
    }

    @Test
    void AC_474_aSecondTransferForAPaidInvoiceIsUnmatched() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal manager = manager();
        long invoice = invoice(owner, 70_000);
        String code = openPayment(owner, invoice);
        sendKeyed(notice(nextTransferId(), 70_000, "SEVQR " + code)).andExpect(status().isOk());
        String again = nextTransferId();
        sendKeyed(notice(again, 70_000, "SEVQR " + code)).andExpect(status().isOk());
        assertThat(reasonOf(manager, again)).isEqualTo("hóa đơn đã thanh toán");
        assertThat(countPayments(invoice)).isEqualTo(1);
    }

    @Test
    void AC_475_noCodeOrUnknownCode() throws Exception {
        AccountPrincipal manager = manager();
        String none = nextTransferId();
        String unknown = nextTransferId();
        sendKeyed(notice(none, 10_000, "chuyen tien an")).andExpect(status().isOk());
        sendKeyed(notice(unknown, 10_000, "SEVQR RH00000000")).andExpect(status().isOk());
        assertThat(reasonOf(manager, none)).isEqualTo("không có mã thanh toán");
        assertThat(reasonOf(manager, unknown)).isEqualTo("mã thanh toán không tồn tại");
    }

    @Test
    void AC_480_aNoticeWithoutTheKeyIsRefusedAndNothingIsRecorded() throws Exception {
        String id = nextTransferId();
        send(null, notice(id, 10_000, "x")).andExpect(status().isUnauthorized());
        send("Apikey wrong-key", notice(id, 10_000, "x")).andExpect(status().isUnauthorized());
        send("Bearer " + KEY, notice(id, 10_000, "x")).andExpect(status().isUnauthorized());
        assertThat(countTransfers(id)).isZero();
    }

    @Test
    void aNoticeOfMoneyOutIsConfirmedAndIgnored() throws Exception {
        String id = nextTransferId();
        Map<String, Object> out = notice(id, 10_000, "x");
        out.put("transferType", "out");
        sendKeyed(out).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(countTransfers(id)).isZero();
    }

    @Test
    void aMalformedNoticeIsRefused() throws Exception {
        sendKeyed(notice(null, 10_000, "x")).andExpect(status().isBadRequest());
        sendKeyed(notice(nextTransferId(), null, "x")).andExpect(status().isBadRequest());
        String id = nextTransferId();
        sendKeyed(notice(id, -5, "x")).andExpect(status().isBadRequest());
        assertThat(countTransfers(id)).isZero();
    }

    @Test
    void AC_479_otherOwnersAndOtherRolesAreRefused() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal other = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 100_000);
        asJson(other, post("/api/payments/invoices/" + invoice + "/open"), Map.of()).andExpect(status().isForbidden());
        as(other, get("/api/payments/invoices/" + invoice)).andExpect(status().isForbidden());
        for (Role role : List.of(Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.CLUB_MANAGER)) {
            AccountPrincipal who = account(role);
            asJson(who, post("/api/payments/invoices/" + invoice + "/open"), Map.of()).andExpect(status().isForbidden());
        }
        for (Role role : List.of(Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER)) {
            AccountPrincipal who = account(role);
            as(who, get("/api/payments/received")).andExpect(status().isForbidden());
            as(who, get("/api/payments/unmatched")).andExpect(status().isForbidden());
            as(who, get("/api/payments/unpaid-invoices")).andExpect(status().isForbidden());
        }
    }

    @Test
    void theOwnerSeesOnlyOwnInvoicesWithTheirStatusAndTheManagerSeesEveryStatus() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal other = account(Role.HORSE_OWNER);
        long paid = invoice(owner, 10_000);
        long unpaid = invoice(owner, 20_000);
        long foreign = invoice(other, 30_000);
        sendKeyed(notice(nextTransferId(), 10_000, "SEVQR " + openPayment(owner, paid))).andExpect(status().isOk());

        JsonNode mine = body(as(owner, get("/api/billing/invoices")));
        assertThat(statusOf(mine, paid)).isEqualTo("PAID");
        assertThat(statusOf(mine, unpaid)).isEqualTo("UNPAID");
        assertThat(statusOf(mine, foreign)).isNull();
        JsonNode all = body(as(manager(), get("/api/billing/invoices")));
        assertThat(statusOf(all, foreign)).isEqualTo("UNPAID");
        assertThat(statusOf(all, paid)).isEqualTo("PAID");
    }

    private static String statusOf(JsonNode invoices, long id) {
        for (JsonNode i : invoices) {
            if (i.get("id").asLong() == id) {
                return i.get("paymentStatus").asText();
            }
        }
        return null;
    }

    private String reasonOf(AccountPrincipal manager, String providerId) throws Exception {
        for (JsonNode t : body(as(manager, get("/api/payments/unmatched")).andExpect(status().isOk()))) {
            if (providerId.equals(jdbc.queryForObject("SELECT provider_id FROM bank_transfer WHERE id = ?",
                    String.class, t.get("id").asLong()))) {
                return t.get("reasonText").asText();
            }
        }
        return null;
    }
}
