package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Statements by horse and category, visible only to their owner and the Club Manager (BR-180, BR-182, AC-353, AC-359). */
class StatementsTest extends BillingTestBase {

    @Test
    void AC_353_chargesAreGroupedByHorseAndCategoryWithATotalThatEqualsTheLines() throws Exception {
        long exam = feeType(200000);
        var feed = supply("FEED", 25000, 100);
        long horse = observedHorse();
        recordExam(horse, exam).andExpect(status().isCreated());
        ration(horse, java.util.Map.of(feed.get("id").asLong(), 2));
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());
        JsonNode statement = statementOf(owner, YearMonth.from(today()));
        assertThat(lines(statement, "EXAM")).hasSize(1);
        assertThat(lines(statement, "FEED")).hasSize(1);
        assertThat(statement.get("horses")).hasSize(1);
        JsonNode group = statement.get("horses").get(0);
        assertThat(group.get("horseId").asLong()).isEqualTo(horse);
        assertThat(group.get("total").decimalValue()).isEqualByComparingTo("250000");
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode c : group.get("categories")) {
            BigDecimal categoryTotal = BigDecimal.ZERO;
            for (JsonNode l : c.get("lines")) {
                categoryTotal = categoryTotal.add(l.get("amount").decimalValue());
            }
            assertThat(c.get("total").decimalValue()).isEqualByComparingTo(categoryTotal);
            sum = sum.add(categoryTotal);
        }
        assertThat(statement.get("total").decimalValue()).isEqualByComparingTo(sum);
        assertThat(statement.get("closed").asBoolean()).isFalse();
    }

    @Test
    void AC_359_anOwnerSeesOnlyOwnHorsesAndOthersAreRefused() throws Exception {
        long type = feeType(100000);
        var other = account(Role.HORSE_OWNER);
        long mine = newHorse();
        long theirs = registerHorse(manager, other, "FEMALE");
        recordExam(mine, type).andExpect(status().isCreated());
        recordExam(theirs, type).andExpect(status().isCreated());
        YearMonth month = YearMonth.from(today());
        assertThat(lines(statementOf(owner, month), "EXAM")).hasSize(1);
        assertThat(lines(statementOf(other, month), "EXAM")).hasSize(1);
        // The Club Manager reads both, an owner cannot ask for another owner's statement or horse.
        assertThat(lines(body(as(manager, get("/api/billing/statements/" + owner.id()).param("month", month.toString()))
                .andExpect(status().isOk())), "EXAM")).hasSize(1);
        as(owner, get("/api/billing/statements/" + other.id()).param("month", month.toString()))
                .andExpect(status().isForbidden());
        costsOf(owner, theirs, month).andExpect(status().isForbidden());
        costsOf(manager, theirs, month).andExpect(status().isOk());
        as(owner, get("/api/billing/statements")).andExpect(status().isForbidden());
        for (Role role : new Role[]{Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM}) {
            var who = account(role);
            as(who, get("/api/billing/my-statement")).andExpect(status().isForbidden());
            as(who, get("/api/billing/statements/" + owner.id())).andExpect(status().isForbidden());
            as(who, get("/api/billing/invoices")).andExpect(status().isForbidden());
            costsOf(who, mine, month).andExpect(status().isForbidden());
        }
    }

    @Test
    void theClubManagerListsEveryOwnerWithChargesAndTheirTotals() throws Exception {
        long type = feeType(120000);
        long horse = newHorse();
        recordExam(horse, type).andExpect(status().isCreated());
        JsonNode rows = body(as(manager, get("/api/billing/statements")
                .param("month", YearMonth.from(today()).toString())).andExpect(status().isOk()));
        boolean found = false;
        for (JsonNode r : rows) {
            if (r.get("ownerId").asLong() == owner.id()) {
                found = true;
                assertThat(r.get("total").decimalValue()).isEqualByComparingTo("120000");
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void aBadMonthIsRefused() throws Exception {
        as(owner, get("/api/billing/my-statement").param("month", "2026/10")).andExpect(status().isBadRequest());
    }
}
