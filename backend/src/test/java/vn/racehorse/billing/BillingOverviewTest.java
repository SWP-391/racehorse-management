package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The totals behind the charts of the billing overview, summed by the service (BR-180, BR-209, AC-448, AC-449). */
class BillingOverviewTest extends BillingTestBase {

    private static final AtomicInteger YEAR = new AtomicInteger(2010);
    private final java.util.List<YearMonth> used = new java.util.ArrayList<>();

    private YearMonth freshMonth() {
        YearMonth month = YearMonth.of(YEAR.incrementAndGet(), 3);
        used.add(month);
        return month;
    }

    @org.junit.jupiter.api.AfterEach
    void closeUsedMonths() throws Exception {
        for (YearMonth month : used) {
            as(manager, post("/api/billing/months/" + month + "/close"));
        }
        used.clear();
    }

    private void charge(long ownerId, YearMonth month, int day, int amount) throws Exception {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("ownerId", ownerId);
        m.put("horseId", null);
        m.put("date", month.atDay(day).toString());
        m.put("amount", amount);
        m.put("reason", "overview");
        asJson(manager, post("/api/billing/adjustments"), m).andExpect(status().isCreated());
    }

    private JsonNode overview(String month) throws Exception {
        return body(as(manager, get("/api/billing/overview").param("month", month)).andExpect(status().isOk()));
    }

    @Test
    void AC_448_aMonthGivesTwelveMonthsEndingAtItAndTheTotalsOfThatMonthOnly() throws Exception {
        YearMonth month = freshMonth();
        long a = owner.id();
        long b = account(Role.HORSE_OWNER).id();
        charge(a, month, 3, 100_000);
        charge(b, month, 4, 300_000);
        charge(a, month.minusMonths(2), 5, 50_000);

        JsonNode o = overview(month.toString());
        assertThat(o.get("scope").asText()).isEqualTo(month.toString());
        assertThat(o.get("total").decimalValue()).isEqualByComparingTo("400000");
        assertThat(o.get("owners").asInt()).isEqualTo(2);
        // The bars are always twelve, the empty months included, and the last one is the chosen month.
        assertThat(o.get("months")).hasSize(12);
        assertThat(o.get("months").get(11).get("month").asText()).isEqualTo(month.toString());
        assertThat(o.get("months").get(11).get("total").decimalValue()).isEqualByComparingTo("400000");
        assertThat(o.get("months").get(9).get("total").decimalValue()).isEqualByComparingTo("50000");
        assertThat(o.get("months").get(10).get("total").decimalValue()).isEqualByComparingTo("0");
        assertThat(o.get("categories").get(0).get("category").asText()).isEqualTo("ADJUSTMENT");
        assertThat(o.get("categories").get(0).get("total").decimalValue()).isEqualByComparingTo("400000");
        assertThat(o.get("topOwners").get(0).get("total").decimalValue()).isEqualByComparingTo("300000");
        assertThat(o.get("topOwners").get(1).get("total").decimalValue()).isEqualByComparingTo("100000");
    }

    @Test
    void AC_448_allMonthsSumsEveryChargeAndAC_449_aMonthWithNothingIsAllZero() throws Exception {
        YearMonth month = freshMonth();
        charge(owner.id(), month, 3, 70_000);

        JsonNode all = overview("all");
        assertThat(all.get("scope").asText()).isEqualTo("all");
        assertThat(all.get("total").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("70000"));
        assertThat(all.get("months").size()).isLessThanOrEqualTo(24);

        JsonNode quiet = overview(YearMonth.of(1995, 1).toString());
        assertThat(quiet.get("total").decimalValue()).isEqualByComparingTo("0");
        assertThat(quiet.get("categories")).isEmpty();
        assertThat(quiet.get("topOwners")).isEmpty();
        assertThat(quiet.get("months")).hasSize(12);
    }

    @Test
    void anythingButTheClubManagerIsRefusedAndABadMonthIsNamed() throws Exception {
        for (var who : java.util.List.of(owner, vet, trainer)) {
            as(who, get("/api/billing/overview")).andExpect(status().isForbidden());
        }
        as(manager, get("/api/billing/overview").param("month", "2026-13")).andExpect(status().isBadRequest());
    }
}
