package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A charge never changes health, lock or sessions of the horse (BR-183, XDEP-023). */
class ChargeIsolationTest extends BillingTestBase {

    @Test
    void chargesLeaveHealthLockAndSessionsAlone() throws Exception {
        long type = feeType(100000);
        var feed = supply("FEED", 1000, 100);
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long session = schedule(plan, today().plusDays(1));
        String stateBefore = state(session);
        JsonNode healthBefore = health(horse);
        recordExam(horse, type).andExpect(status().isCreated());
        ration(horse, Map.of(feed.get("id").asLong(), 3));
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());
        asJson(manager, post("/api/billing/adjustments"), Map.of("ownerId", owner.id(), "horseId", horse,
                "date", today().toString(), "amount", 5000, "reason", "thử")).andExpect(status().isCreated());
        assertThat(lines(statementOf(owner, YearMonth.from(today())), "FEED")).hasSize(1);
        assertThat(state(session)).isEqualTo(stateBefore);
        JsonNode healthAfter = health(horse);
        assertThat(healthAfter.get("status")).isEqualTo(healthBefore.get("status"));
        assertThat(healthAfter.get("lock")).isEqualTo(healthBefore.get("lock"));
    }
}
