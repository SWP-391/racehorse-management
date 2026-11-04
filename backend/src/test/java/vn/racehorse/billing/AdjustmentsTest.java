package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Correction lines instead of editing a closed invoice (BR-180, AC-357). */
class AdjustmentsTest extends BillingTestBase {

    private Map<String, Object> body(long ownerId, String date, Object amount, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("ownerId", ownerId);
        m.put("date", date);
        m.put("amount", amount);
        m.put("reason", reason);
        return m;
    }

    @Test
    void AC_357_aCreditLineInAnOpenMonthShowsOnThatMonthsStatement() throws Exception {
        YearMonth month = YearMonth.from(today());
        asJson(manager, post("/api/billing/adjustments"), body(owner.id(), today().toString(), -25000, "giảm trừ"))
                .andExpect(status().isCreated());
        JsonNode lines = statementOf(owner, month);
        var adjustments = lines(lines, "ADJUSTMENT");
        assertThat(adjustments).hasSize(1);
        assertThat(adjustments.get(0).get("amount").decimalValue()).isEqualByComparingTo("-25000");
        assertThat(adjustments.get(0).get("reason").asText()).isEqualTo("giảm trừ");
        assertThat(auditActions(manager)).contains("BILLING_ADJUSTMENT_ADDED");
    }

    @Test
    void aMissingReasonOrAZeroAmountIsRefused() throws Exception {
        asJson(manager, post("/api/billing/adjustments"), body(owner.id(), today().toString(), 5000, "  "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.reason").exists());
        asJson(manager, post("/api/billing/adjustments"), body(owner.id(), today().toString(), 0, "không"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.amount").exists());
        asJson(manager, post("/api/billing/adjustments"), body(owner.id(), today().toString(), 10.5, "lẻ"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.amount").exists());
        asJson(manager, post("/api/billing/adjustments"), body(account(Role.GROOM).id(), today().toString(), 5, "sai"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_OWNER"));
    }

    @Test
    void aDateInAClosedMonthIsRefusedAndTheDatabaseGuardsClosedCharges() throws Exception {
        YearMonth month = YearMonth.of(1990, 6);
        asJson(manager, post("/api/billing/adjustments"), body(owner.id(), month.atDay(3).toString(), 1000, "mở"))
                .andExpect(status().isCreated());
        as(manager, post("/api/billing/months/" + month + "/close")).andExpect(status().isOk());
        asJson(manager, post("/api/billing/adjustments"), body(owner.id(), month.atDay(4).toString(), 1000, "muộn"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));
        // A closed charge cannot be changed even by a direct statement (the trigger, not only the service).
        assertThatThrownBy(() -> jdbc.update("UPDATE charge SET amount = 1 WHERE invoice_id IS NOT NULL "
                + "AND owner_id = ?", owner.id())).hasMessageContaining("closed invoice");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoice_line WHERE invoice_id IN "
                + "(SELECT id FROM invoice WHERE owner_id = ?)", owner.id()));
    }

    @Test
    void onlyTheClubManagerAddsCorrections() throws Exception {
        for (Role role : new Role[]{Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER}) {
            asJson(account(role), post("/api/billing/adjustments"), body(owner.id(), today().toString(), 100, "x"))
                    .andExpect(status().isForbidden());
        }
        as(owner, get("/api/billing/my-statement")).andExpect(status().isOk());
    }
}
