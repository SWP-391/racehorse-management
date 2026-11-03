package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import vn.racehorse.stock.service.SupplyUsed;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Medicine and feed charges follow the stock ledger (BR-179, XDEP-023, TECH-166, AC-353, AC-355). */
class SupplyChargesTest extends BillingTestBase {

    @Autowired
    private ApplicationEventPublisher events;

    private long careItem(long horse) throws Exception {
        return body(asJson(vet, post("/api/horses/" + horse + "/care"), Map.of("type", "VACCINATION", "name", "Cúm",
                "intervalDays", 90, "firstDue", today().plusDays(3).toString())).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    @Test
    void AC_353_aVaccinationAndAFeedingDayAppearOnTheOwnersStatement() throws Exception {
        JsonNode medicine = supply("MEDICINE", 40000, 5);
        JsonNode feed = supply("FEED", 25000, 100);
        long horse = observedHorse();
        long item = careItem(horse);
        Map<String, Object> completion = new HashMap<>();
        completion.put("dateDone", today().toString());
        completion.put("performer", "BS. Test");
        completion.put("supplyItemId", medicine.get("id").asLong());
        completion.put("quantity", 2);
        asJson(vet, post("/api/care/" + item + "/complete"), completion).andExpect(status().isOk());
        ration(horse, Map.of(feed.get("id").asLong(), 6));
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());

        JsonNode statement = statementOf(owner, YearMonth.from(today()));
        var meds = lines(statement, "MEDICINE");
        var feeds = lines(statement, "FEED");
        assertThat(meds).hasSize(1);
        assertThat(meds.get(0).get("amount").decimalValue()).isEqualByComparingTo("80000");
        assertThat(feeds).hasSize(1);
        assertThat(feeds.get(0).get("amount").decimalValue()).isEqualByComparingTo("150000");
        assertThat(statement.get("total").decimalValue()).isEqualByComparingTo("230000");
        // The price of that moment is kept even when the supply price changes afterwards.
        asJson(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/supplies/" + feed.get("id").asLong() + "/price"), Map.of("price", 99999)).andExpect(status().isOk());
        assertThat(lines(statementOf(owner, YearMonth.from(today())), "FEED").get(0).get("amount").decimalValue())
                .isEqualByComparingTo("150000");
    }

    @Test
    void AC_355_cancellingTheMarkRemovesThatDaysFeedCharge() throws Exception {
        JsonNode feed = supply("FEED", 25000, 100);
        long horse = observedHorse();
        ration(horse, Map.of(feed.get("id").asLong(), 4));
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());
        assertThat(lines(statementOf(owner, YearMonth.from(today())), "FEED")).hasSize(1);
        long entry = 0;
        for (JsonNode h : body(as(defaultGroom(), get("/api/chores"))).get("horses")) {
            if (h.get("horseId").asLong() == horse) {
                for (JsonNode c : h.get("chores")) {
                    if (c.get("chore").asText().equals("FEEDING")) {
                        entry = c.get("entryId").asLong();
                    }
                }
            }
        }
        asJson(defaultGroom(), post("/api/chore-entries/" + entry + "/cancel"), Map.of("reason", "đánh dấu nhầm"))
                .andExpect(status().isOk());
        assertThat(lines(statementOf(owner, YearMonth.from(today())), "FEED")).isEmpty();
        // Marked again, it is charged again (a new fact).
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());
        assertThat(lines(statementOf(owner, YearMonth.from(today())), "FEED")).hasSize(1);
    }

    @Test
    void aMarkRefusedForShortStockLeavesNoCharge() throws Exception {
        JsonNode feed = supply("FEED", 1000, 2);
        long horse = observedHorse();
        ration(horse, Map.of(feed.get("id").asLong(), 6));
        markFeeding(defaultGroom(), horse).andExpect(status().isConflict());
        assertThat(lines(statementOf(owner, YearMonth.from(today())), "FEED")).isEmpty();
    }

    @Test
    void theSameEventTwiceCreatesOneCharge() throws Exception {
        JsonNode feed = supply("FEED", 1000, 50);
        long horse = observedHorse();
        long id = feed.get("id").asLong();
        tx.executeWithoutResult(s -> {
            SupplyUsed e = new SupplyUsed(horse, SupplyUsed.Source.FEED, id, 3, new BigDecimal("1000"), today(),
                    "CHORE_ENTRY", 987654L);
            events.publishEvent(e);
            events.publishEvent(e);
        });
        var feeds = lines(statementOf(owner, YearMonth.from(today())), "FEED");
        assertThat(feeds).hasSize(1);
        assertThat(feeds.get(0).get("amount").decimalValue()).isEqualByComparingTo("3000");
    }
}
