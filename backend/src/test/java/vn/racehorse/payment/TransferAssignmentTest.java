package vn.racehorse.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Club Manager's lists and the assignment of an unmatched transfer (AC-476..AC-479, AC-483). */
class TransferAssignmentTest extends PaymentTestBase {

    private long unmatched(String id, long amount) throws Exception {
        sendKeyed(notice(id, amount, "chuyen tien khong ma")).andExpect(status().isOk());
        return jdbc.queryForObject("SELECT id FROM bank_transfer WHERE provider_id = ?", Long.class, id);
    }

    private Map<String, Object> assign(long invoice, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("invoiceId", invoice);
        m.put("reason", reason);
        return m;
    }

    @Test
    void AC_476_assigningAPaymentPaysTheInvoiceAndIsAudited() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal manager = manager();
        long invoice = invoice(owner, 1_500_000);
        long transfer = unmatched(nextTransferId(), 1_000_000);

        asJson(manager, post("/api/payments/transfers/" + transfer + "/assign"),
                assign(invoice, "Chủ ngựa chuyển thiếu, đã nhận tiền mặt phần còn lại")).andExpect(status().isNoContent());

        assertThat(paymentOf(owner, invoice).get("status").asText()).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT status FROM bank_transfer WHERE id = ?", String.class, transfer))
                .isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT via FROM invoice_payment WHERE invoice_id = ?", String.class, invoice))
                .isEqualTo("ASSIGNED");
        JsonNode audit = body(as(manager, get("/api/audit").param("actorId", manager.id().toString())));
        assertThat(audit.toString()).contains("Chủ ngựa chuyển thiếu");
        // an assigned transfer is still listed, as assigned
        boolean listed = false;
        for (JsonNode t : body(as(manager, get("/api/payments/unmatched")))) {
            listed |= t.get("id").asLong() == transfer && "ASSIGNED".equals(t.get("status").asText());
        }
        assertThat(listed).isTrue();
    }

    @Test
    void AC_477_noReasonIsRefusedNamingTheReasonField() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal manager = manager();
        long invoice = invoice(owner, 10_000);
        long transfer = unmatched(nextTransferId(), 10_000);
        asJson(manager, post("/api/payments/transfers/" + transfer + "/assign"), assign(invoice, "  "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.reason").exists());
        asJson(manager, post("/api/payments/transfers/" + transfer + "/assign"), assign(invoice, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.reason").exists());
        assertThat(countPayments(invoice)).isZero();
    }

    @Test
    void AC_478_aPaidInvoiceIsRefused() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal manager = manager();
        long invoice = invoice(owner, 10_000);
        sendKeyed(notice(nextTransferId(), 10_000, "SEVQR " + openPayment(owner, invoice))).andExpect(status().isOk());
        long transfer = unmatched(nextTransferId(), 10_000);
        asJson(manager, post("/api/payments/transfers/" + transfer + "/assign"), assign(invoice, "thử"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM bank_transfer WHERE id = ?", String.class, transfer))
                .isEqualTo("UNMATCHED");
    }

    @Test
    void aTransferAlreadyAssignedIsRefused() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal manager = manager();
        long first = invoice(owner, 10_000);
        long second = invoice(owner, 10_000);
        long transfer = unmatched(nextTransferId(), 10_000);
        asJson(manager, post("/api/payments/transfers/" + transfer + "/assign"), assign(first, "lần một"))
                .andExpect(status().isNoContent());
        asJson(manager, post("/api/payments/transfers/" + transfer + "/assign"), assign(second, "lần hai"))
                .andExpect(status().isConflict());
        assertThat(countPayments(second)).isZero();
    }

    @Test
    void anAppliedTransferCannotBeAssigned() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal manager = manager();
        long invoice = invoice(owner, 10_000);
        String id = nextTransferId();
        sendKeyed(notice(id, 10_000, "SEVQR " + openPayment(owner, invoice))).andExpect(status().isOk());
        long applied = jdbc.queryForObject("SELECT id FROM bank_transfer WHERE provider_id = ?", Long.class, id);
        long other = invoice(owner, 10_000);
        asJson(manager, post("/api/payments/transfers/" + applied + "/assign"), assign(other, "thử"))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyTheClubManagerAssigns() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 10_000);
        long transfer = unmatched(nextTransferId(), 10_000);
        for (Role role : Role.values()) {
            if (role != Role.CLUB_MANAGER) {
                asJson(account(role), post("/api/payments/transfers/" + transfer + "/assign"), assign(invoice, "x"))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void AC_483_testModePaymentsAreLabelledSimulated() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal manager = manager();
        long invoice = invoice(owner, 15_000);
        String id = nextTransferId();
        sendKeyed(notice(id, 15_000, "SEVQR " + openPayment(owner, invoice))).andExpect(status().isOk());
        boolean simulated = false;
        for (JsonNode t : body(as(manager, get("/api/payments/received")))) {
            if (t.get("invoiceId").asLong() == invoice) {
                simulated = t.get("simulated").asBoolean();
            }
        }
        assertThat(simulated).isTrue();
        assertThat(paymentOf(owner, invoice).get("simulated").asBoolean()).isTrue();
    }

    @Test
    void theUnpaidInvoicesAreOfferedForAssignment() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal manager = manager();
        long unpaid = invoice(owner, 10_000);
        long paid = invoice(owner, 20_000);
        sendKeyed(notice(nextTransferId(), 20_000, "SEVQR " + openPayment(owner, paid))).andExpect(status().isOk());
        String listed = body(as(manager, get("/api/payments/unpaid-invoices"))).toString();
        assertThat(listed).contains("\"id\":" + unpaid + ",").doesNotContain("\"id\":" + paid + ",");
    }
}
