package vn.racehorse.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fixtures for the payment tests: invoices written straight into the table (they are made by closing a month elsewhere). */
public abstract class PaymentTestBase extends ApiTest {

    protected static final String KEY = "test-sepay-key";
    private static final AtomicInteger YEAR = new AtomicInteger(1500);
    private static final AtomicInteger TRANSFER = new AtomicInteger();

    @Autowired
    protected JdbcTemplate jdbc;

    protected AccountPrincipal manager() {
        return account(Role.CLUB_MANAGER);
    }

    /** A closed invoice of the owner for a month of its own, so the shared database never sees a clash. */
    protected long invoice(AccountPrincipal owner, long total) {
        String month = YEAR.incrementAndGet() + "-03";
        return jdbc.queryForObject("INSERT INTO invoice (owner_id, month, number, total, closed_at) "
                + "VALUES (?, ?, ?, ?, now()) RETURNING id", Long.class, owner.id(), month,
                "HD-" + month + "-" + owner.id(), BigDecimal.valueOf(total));
    }

    protected String openPayment(AccountPrincipal owner, long invoiceId) throws Exception {
        return body(asJson(owner, post("/api/payments/invoices/" + invoiceId + "/open"), Map.of())
                .andExpect(status().isOk())).get("paymentCode").asText();
    }

    protected static String nextTransferId() {
        return "T" + System.nanoTime() + "-" + TRANSFER.incrementAndGet();
    }

    protected Map<String, Object> notice(String id, Object amount, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("gateway", "VietinBank");
        m.put("transactionDate", "2026-11-12 14:02:37");
        m.put("accountNumber", "0000000001");
        m.put("code", null);
        m.put("content", content);
        m.put("transferType", "in");
        m.put("transferAmount", amount);
        m.put("accumulated", 0);
        m.put("referenceCode", "FT123");
        m.put("description", "BankAPINotify " + content);
        return m;
    }

    /** What the bank-transfer service does: no session, no CSRF token, only the key. */
    protected ResultActions send(String authorization, Object body) throws Exception {
        var request = post("/api/payments/sepay").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mvc.perform(request);
    }

    protected ResultActions sendKeyed(Object body) throws Exception {
        return send("Apikey " + KEY, body);
    }

    protected JsonNode paymentOf(AccountPrincipal owner, long invoiceId) throws Exception {
        return body(as(owner, get("/api/payments/invoices/" + invoiceId)).andExpect(status().isOk()));
    }

    protected long countPayments(long invoiceId) {
        return jdbc.queryForObject("SELECT count(*) FROM invoice_payment WHERE invoice_id = ?", Long.class, invoiceId);
    }

    protected long countTransfers(String providerId) {
        return jdbc.queryForObject("SELECT count(*) FROM bank_transfer WHERE provider_id = ?", Long.class, providerId);
    }
}
