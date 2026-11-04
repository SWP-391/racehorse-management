package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The service price list: effective dates, never in the past, Club Manager only (BR-178, BR-182, AC-352, AC-354). */
class BillingPricesTest extends BillingTestBase {

    private JsonNode typeView(JsonNode list, long id) {
        for (JsonNode t : list.get("feeTypes")) {
            if (t.get("id").asLong() == id) {
                return t;
            }
        }
        throw new AssertionError("fee type " + id + " missing");
    }

    @Test
    void AC_352_aPriceIsSavedWithItsDateAndListed() throws Exception {
        long id = feeType(200000);
        LocalDate later = today().plusDays(30);
        setPrice("EXAM_FEE", id, 250000, later).andExpect(status().isCreated());
        JsonNode list = body(as(manager, get("/api/billing/prices")).andExpect(status().isOk()));
        JsonNode view = typeView(list, id);
        assertThat(view.get("currentPrice").decimalValue()).isEqualByComparingTo("200000");
        assertThat(view.get("prices")).hasSize(2);
        assertThat(view.get("prices").get(0).get("effectiveFrom").asText()).isEqualTo(later.toString());
        assertThat(auditActions(manager)).contains("FEE_TYPE_CREATED", "BILLING_PRICE_SET");
    }

    @Test
    void AC_354_aPriceInThePastIsRefusedSoRecordedWorkKeepsItsPrice() throws Exception {
        long id = feeType(200000);
        setPrice("EXAM_FEE", id, 999, today().minusDays(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_DATE_IN_PAST"));
        assertThat(typeView(body(as(manager, get("/api/billing/prices"))), id).get("prices")).hasSize(1);
    }

    @Test
    void invalidAmountsAndDuplicateDatesAreRefused() throws Exception {
        long id = feeType(200000);
        setPrice("EXAM_FEE", id, -5, today().plusDays(2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").exists());
        setPrice("EXAM_FEE", id, 100.5, today().plusDays(2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").exists());
        setPrice("EXAM_FEE", id, 300000, today()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_DATE_TAKEN"));
        setPrice("EXAM_FEE", null, 300000, today().plusDays(2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FEE_TYPE"));
    }

    @Test
    void AC_452_aFeeTypeIsCreatedTogetherWithItsFirstPrice() throws Exception {
        long id = body(asJson(manager, post("/api/billing/fee-types"),
                Map.of("name", unique("Khám da liễu"), "amount", 200000, "effectiveFrom", today().toString()))
                .andExpect(status().isCreated())).get("id").asLong();
        JsonNode view = typeView(body(as(manager, get("/api/billing/prices"))), id);
        assertThat(view.get("currentPrice").decimalValue()).isEqualByComparingTo("200000");
        assertThat(view.get("prices")).hasSize(1);
    }

    @Test
    void AC_453_aFeeTypeWithoutAPriceIsRefusedAndNothingIsSaved() throws Exception {
        String name = unique("Không giá");
        asJson(manager, post("/api/billing/fee-types"), Map.of("name", name))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.amount").exists());
        // A price in the past is refused too, and the type is not left behind without a price.
        asJson(manager, post("/api/billing/fee-types"),
                Map.of("name", name, "amount", 1000, "effectiveFrom", today().minusDays(1).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRICE_DATE_IN_PAST"));
        for (JsonNode t : body(as(manager, get("/api/billing/prices"))).get("feeTypes")) {
            assertThat(t.get("name").asText()).isNotEqualTo(name);
        }
    }

    @Test
    void aFeeTypeNameIsUniqueAndTypesAreDeactivatedNotDeleted() throws Exception {
        String name = unique("Điều trị");
        long id = body(asJson(manager, post("/api/billing/fee-types"),
                Map.of("name", name, "amount", 120000, "effectiveFrom", today().toString()))
                .andExpect(status().isCreated())).get("id").asLong();
        asJson(manager, post("/api/billing/fee-types"),
                Map.of("name", name.toUpperCase(), "amount", 120000, "effectiveFrom", today().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_FEE_TYPE"));
        asJson(manager, post("/api/billing/fee-types/" + id + "/deactivate"), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(typeView(body(as(manager, get("/api/billing/prices"))), id).get("active").asBoolean()).isFalse();
    }

    @Test
    void BR_182_onlyTheClubManagerSeesAndSetsPrices() throws Exception {
        for (Role role : new Role[]{Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER}) {
            var who = account(role);
            as(who, get("/api/billing/prices")).andExpect(status().isForbidden());
            asJson(who, post("/api/billing/prices"), Map.of("kind", "DAILY_FEE", "amount", 1,
                    "effectiveFrom", today().plusDays(900).toString())).andExpect(status().isForbidden());
            asJson(who, post("/api/billing/fee-types"), Map.of("name", unique("x"))).andExpect(status().isForbidden());
        }
    }
}
