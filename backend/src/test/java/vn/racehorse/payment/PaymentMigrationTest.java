package vn.racehorse.payment;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The constraints of V23 (TECH-231) and the routes around the one public notice path (TECH-234). */
class PaymentMigrationTest extends PaymentTestBase {

    private long transfer(String providerId, String status, Long invoiceId) {
        return jdbc.queryForObject("INSERT INTO bank_transfer (provider, provider_id, amount, received_at, simulated, status, invoice_id) "
                + "VALUES ('SEPAY', ?, 100, now(), true, ?, ?) RETURNING id", Long.class, providerId, status, invoiceId);
    }

    @Test
    void aTransferIdIsRecordedOnce() {
        String id = nextTransferId();
        transfer(id, "UNMATCHED", null);
        assertThatThrownBy(() -> transfer(id, "UNMATCHED", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anInvoiceHasOnePayingTransferAndOneCode() {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 100);
        transfer(nextTransferId(), "APPLIED", invoice);
        assertThatThrownBy(() -> transfer(nextTransferId(), "ASSIGNED", invoice))
                .isInstanceOf(DataIntegrityViolationException.class);
        // an unmatched transfer may name the invoice it was meant for without paying it
        transfer(nextTransferId(), "UNMATCHED", invoice);

        jdbc.update("INSERT INTO invoice_payment_code (invoice_id, code, created_at) VALUES (?, 'RH99999991', now())", invoice);
        long other = invoice(owner, 100);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO invoice_payment_code (invoice_id, code, created_at) VALUES (?, 'RH99999991', now())", other))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void everyOtherPaymentRouteStillNeedsASessionAndAToken() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long invoice = invoice(owner, 100);
        mvc.perform(post("/api/payments/invoices/" + invoice + "/open")).andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/received")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments/unmatched")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments/invoices/" + invoice)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/transfers/1/assign")).andExpect(status().isForbidden());
        assertThat(true).isTrue();
    }
}
