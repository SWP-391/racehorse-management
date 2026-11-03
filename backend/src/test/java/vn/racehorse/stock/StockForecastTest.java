package vn.racehorse.stock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.racehorse.common.TestClock;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Days of cover, the club-wide threshold and the two states (BR-175, BR-177, AC-333, AC-334, AC-337). */
class StockForecastTest extends StockTestBase {

    @AfterEach
    void restoreThreshold() throws Exception {
        asJson(manager, put("/api/settings/stock-warning"), Map.of("days", 7)).andExpect(status().isOk());
    }

    private long horseEating(long supplyId, Object perDay) throws Exception {
        long horse = observedHorse();
        ration(horse, Map.of(supplyId, perDay));
        return horse;
    }

    @Test
    void AC_333_sixDaysLeftAgainstAThresholdOfSevenIsCanNhap() throws Exception {
        long id = supply("FEED", 1000, 60).get("id").asLong();
        horseEating(id, 10);
        JsonNode item = itemOf(id);
        assertThat(item.get("dailyUse").asLong()).isEqualTo(10);
        assertThat(item.get("daysOfCover").asLong()).isEqualTo(6);
        assertThat(item.get("state").asText()).isEqualTo("RESTOCK");
    }

    @Test
    void AC_334_anExpectedReceiptCountsUntilItsDatePasses() throws Exception {
        long id = supply("FEED", 1000, 60).get("id").asLong();
        horseEating(id, 10);
        asJson(manager, post("/api/supplies/" + id + "/expected-receipts"),
                Map.of("expectedDate", today().plusDays(1).toString(), "quantity", 100)).andExpect(status().isCreated());
        JsonNode item = itemOf(id);
        assertThat(item.get("daysOfCover").asLong()).isEqualTo(16);
        assertThat(item.get("state").asText()).isEqualTo("OK");

        // Two days later the delivery is overdue and no longer counted.
        shiftToClubDay(2, 9);
        JsonNode later = itemOf(id);
        assertThat(later.get("daysOfCover").asLong()).isEqualTo(6);
        assertThat(later.get("state").asText()).isEqualTo("RESTOCK");
        TestClock.reset();
    }

    @Test
    void anItemWithNothingLeftIsHetHangAndAnUnusedItemHasNoDaysOfCover() throws Exception {
        long empty = supply("FEED", 1000, 0).get("id").asLong();
        horseEating(empty, 3);
        JsonNode out = itemOf(empty);
        assertThat(out.get("state").asText()).isEqualTo("OUT");
        assertThat(out.get("daysOfCover").asLong()).isZero();

        long unused = supply("FEED", 1000, 40).get("id").asLong();
        JsonNode idle = itemOf(unused);
        assertThat(idle.has("daysOfCover")).isFalse();
        assertThat(idle.get("state").asText()).isEqualTo("OK");
    }

    @Test
    void AC_337_theThresholdIsAClubSettingBetweenOneAndThreeHundredSixtyFive() throws Exception {
        long id = supply("FEED", 1000, 90).get("id").asLong();
        horseEating(id, 10);
        assertThat(itemOf(id).get("daysOfCover").asLong()).isEqualTo(9);
        assertThat(itemOf(id).get("state").asText()).isEqualTo("OK");
        asJson(manager, put("/api/settings/stock-warning"), Map.of("days", 10)).andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(10));
        assertThat(itemOf(id).get("state").asText()).isEqualTo("RESTOCK");
        as(manager, get("/api/settings/stock-warning")).andExpect(jsonPath("$.days").value(10));
        for (Object bad : new Object[]{0, 366, -3, 2.5}) {
            asJson(manager, put("/api/settings/stock-warning"), Map.of("days", bad))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STOCK_WARNING"));
        }
        for (var who : java.util.List.of(vet, trainer, owner, defaultGroom())) {
            asJson(who, put("/api/settings/stock-warning"), Map.of("days", 5)).andExpect(status().isForbidden());
        }
        assertThat(auditActions(manager)).contains("SETTING_CHANGED");
    }

    @Test
    void anExpectedReceiptInThePastOrACancelledOneIsRefusedOrIgnored() throws Exception {
        long id = supply("FEED", 1000, 60).get("id").asLong();
        horseEating(id, 10);
        asJson(manager, post("/api/supplies/" + id + "/expected-receipts"),
                Map.of("expectedDate", LocalDate.now().minusDays(3).toString(), "quantity", 10)).andExpect(status().isBadRequest());
        long er = body(asJson(manager, post("/api/supplies/" + id + "/expected-receipts"),
                Map.of("expectedDate", today().plusDays(2).toString(), "quantity", 100)).andExpect(status().isCreated()))
                .get("id").asLong();
        assertThat(itemOf(id).get("daysOfCover").asLong()).isEqualTo(16);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/supplies/" + id + "/expected-receipts/" + er)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(manager))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());
        assertThat(itemOf(id).get("daysOfCover").asLong()).isEqualTo(6);
        // A receipt linked to an expected one closes it.
        long second = body(asJson(manager, post("/api/supplies/" + id + "/expected-receipts"),
                Map.of("expectedDate", today().plusDays(2).toString(), "quantity", 100)).andExpect(status().isCreated()))
                .get("id").asLong();
        asJson(manager, post("/api/supplies/" + id + "/receipts"), Map.of("quantity", 100, "expectedReceiptId", second))
                .andExpect(status().isCreated());
        assertThat(itemOf(id).get("daysOfCover").asLong()).isEqualTo(16);
        assertThat(body(as(manager, get("/api/supplies/" + id + "/expected-receipts")))).isEmpty();
    }
}
