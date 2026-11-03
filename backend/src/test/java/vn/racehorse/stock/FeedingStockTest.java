package vn.racehorse.stock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.common.TestClock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Marking "Cho ăn" takes the day's ration out of stock; cancel and back-fill (BR-173, BR-174, AC-345 to AC-351). */
class FeedingStockTest extends StockTestBase {

    private long entryOf(long horse) throws Exception {
        for (JsonNode h : body(as(defaultGroom(), get("/api/chores")).andExpect(status().isOk())).get("horses")) {
            if (h.get("horseId").asLong() == horse) {
                for (JsonNode c : h.get("chores")) {
                    if (c.get("chore").asText().equals("FEEDING")) {
                        return c.get("entryId").asLong();
                    }
                }
            }
        }
        throw new AssertionError("no feeding entry");
    }

    private boolean feedingDone(long horse) throws Exception {
        for (JsonNode h : body(as(defaultGroom(), get("/api/chores")).andExpect(status().isOk())).get("horses")) {
            if (h.get("horseId").asLong() == horse) {
                for (JsonNode c : h.get("chores")) {
                    if (c.get("chore").asText().equals("FEEDING")) {
                        return c.get("done").asBoolean();
                    }
                }
            }
        }
        throw new AssertionError("horse not on the list");
    }

    @Test
    void AC_345_markingFeedingTakesTheRationOutOfStock() throws Exception {
        long id = supply("FEED", 25000, 100).get("id").asLong();
        long horse = observedHorse();
        ration(horse, Map.of(id, 6));
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());
        assertThat(balance(id)).isEqualTo(94);
        JsonNode line = movements(id).get(0);
        assertThat(line.get("kind").asText()).isEqualTo("USE_FEED");
        assertThat(line.get("delta").asLong()).isEqualTo(-6);
        assertThat(line.get("refType").asText()).isEqualTo("CHORE_ENTRY");
        assertThat(line.get("unitPrice").decimalValue()).isEqualByComparingTo("25000");
        assertThat(ledgerSum(id)).isEqualTo(94);
    }

    @Test
    void AC_346_shortStockRefusesTheMarkAndTellsTheClubManagerOnce() throws Exception {
        JsonNode item = supply("FEED", 1000, 4);
        long id = item.get("id").asLong();
        long horse = observedHorse();
        ration(horse, Map.of(id, 6));
        markFeeding(defaultGroom(), horse).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.fields['" + item.get("name").asText() + "']").exists());
        assertThat(balance(id)).isEqualTo(4);
        assertThat(feedingDone(horse)).isFalse();
        assertThat(movements(id)).hasSize(1);
        String name = item.get("name").asText();
        assertThat(notificationsContaining(manager, name)).isEqualTo(1);
        // A second try within ten minutes is refused again without a second notice.
        markFeeding(defaultGroom(), horse).andExpect(status().isConflict());
        assertThat(notificationsContaining(manager, name)).isEqualTo(1);
    }

    @Test
    void AC_347_cancellingPutsTheStockBackAtTheOriginalPrice() throws Exception {
        long id = supply("FEED", 25000, 100).get("id").asLong();
        long horse = observedHorse();
        ration(horse, Map.of(id, 6));
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());
        long entry = entryOf(horse);
        asJson(manager, put("/api/supplies/" + id + "/price"), Map.of("price", 40000)).andExpect(status().isOk());
        asJson(defaultGroom(), post("/api/chore-entries/" + entry + "/cancel"), Map.of("reason", "đánh dấu nhầm"))
                .andExpect(status().isOk());
        assertThat(balance(id)).isEqualTo(100);
        JsonNode reversal = movements(id).get(0);
        assertThat(reversal.get("kind").asText()).isEqualTo("REVERSAL");
        assertThat(reversal.get("delta").asLong()).isEqualTo(6);
        assertThat(reversal.get("unitPrice").decimalValue()).isEqualByComparingTo("25000");
        assertThat(ledgerSum(id)).isEqualTo(100);
        asJson(defaultGroom(), post("/api/chore-entries/" + entry + "/cancel"), Map.of("reason", "lần hai"))
                .andExpect(status().isConflict());
        assertThat(balance(id)).isEqualTo(100);
        // It may be marked again and takes the stock again.
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());
        assertThat(balance(id)).isEqualTo(94);
    }

    @Test
    void AC_348_aHorseWithoutARationTakesNothing() throws Exception {
        long id = supply("FEED", 1000, 10).get("id").asLong();
        long horse = observedHorse();
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());
        assertThat(feedingDone(horse)).isTrue();
        assertThat(balance(id)).isEqualTo(10);
    }

    @Test
    void theDailyTotalOfAnItemIsRoundedUpToAWholeUnit() throws Exception {
        long id = supply("FEED", 1000, 100).get("id").asLong();
        long horse = observedHorse();
        twoMealRation(horse, id, 2.5, 4);
        markFeeding(defaultGroom(), horse).andExpect(status().isCreated());
        assertThat(balance(id)).isEqualTo(93);
    }

    @Test
    void AC_349_theClubManagerFillsAPastDayWithTheRationOfThatDay() throws Exception {
        long id = supply("FEED", 1000, 100).get("id").asLong();
        long horse = observedHorse();
        ration(horse, Map.of(id, 2));
        LocalDate day0 = today();
        shiftToClubDay(1, 9);
        ration(horse, Map.of(id, 4));
        shiftToClubDay(3, 9);
        // Day 0 was under version 1 (2 units), day 2 under version 2 (4 units).
        backfill(manager, horse, day0.toString(), "quên đánh dấu").andExpect(status().isCreated())
                .andExpect(jsonPath("$.backfilled").value(true));
        assertThat(balance(id)).isEqualTo(98);
        backfill(manager, horse, day0.plusDays(2).toString(), "quên đánh dấu").andExpect(status().isCreated());
        assertThat(balance(id)).isEqualTo(94);
        JsonNode line = movements(id).get(0);
        assertThat(line.get("occurredOn").asText()).isEqualTo(day0.plusDays(2).toString());
        assertThat(auditActions(manager)).contains("CHORE_BACKFILLED");
        TestClock.reset();
    }

    @Test
    void AC_350_aBackFillNeedsAReasonAPastDayAndAHorseManagedThatDay() throws Exception {
        long id = supply("FEED", 1000, 100).get("id").asLong();
        long horse = observedHorse();
        ration(horse, Map.of(id, 2));
        LocalDate day0 = today();
        shiftToClubDay(2, 9);
        backfill(manager, horse, day0.toString(), " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").exists());
        backfill(manager, horse, today().toString(), "hôm nay").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATE_NOT_PAST"));
        backfill(manager, horse, today().plusDays(1).toString(), "mai").andExpect(status().isBadRequest());
        backfill(manager, horse, day0.minusDays(1).toString(), "trước ngày nhập đàn").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HORSE_NOT_YET_MANAGED"));
        backfill(manager, horse, day0.toString(), "ok").andExpect(status().isCreated());
        backfill(manager, horse, day0.toString(), "lần hai").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHORE_ALREADY_DONE"));
        assertThat(balance(id)).isEqualTo(98);
        TestClock.reset();
    }

    @Test
    void AC_351_aGroomCannotBackFillAndTodayStaysTheOnlyDayThatCanBeMarked() throws Exception {
        long id = supply("FEED", 1000, 100).get("id").asLong();
        long horse = observedHorse();
        ration(horse, Map.of(id, 2));
        shiftToClubDay(2, 9);
        for (var who : List.of(defaultGroom(), vet, trainer, owner)) {
            backfill(who, horse, today().minusDays(1).toString(), "thử").andExpect(status().isForbidden());
        }
        asJson(defaultGroom(), post("/api/horses/" + horse + "/chores/FEEDING/done"),
                Map.of("date", today().minusDays(1).toString())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHORE_NOT_TODAY"));
        assertThat(balance(id)).isEqualTo(100);
        TestClock.reset();
    }

    @Test
    void tenHorsesFedAtOnceSharingAnItemKeepTheBalanceEqualToTheLedger() throws Exception {
        long id = supply("FEED", 1000, 100).get("id").asLong();
        List<Long> horses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long horse = observedHorse();
            ration(horse, Map.of(id, 3));
            horses.add(horse);
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long horse : horses) {
                Callable<Integer> mark = () -> markFeeding(defaultGroom(), horse).andReturn().getResponse().getStatus();
                results.add(pool.submit(mark));
            }
            for (Future<Integer> r : results) {
                assertThat(r.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(balance(id)).isEqualTo(70);
        assertThat(ledgerSum(id)).isEqualTo(70);
    }

    @Test
    void anArchivedHorseCannotBeBackFilledAfterItsArchiveDate() throws Exception {
        long id = supply("FEED", 1000, 10).get("id").asLong();
        long horse = observedHorse();
        ration(horse, Map.of(id, 2));
        LocalDate day0 = today();
        asJson(manager, post("/api/horses/" + horse + "/archive"), new HashMap<>(Map.of("reason", "SOLD", "note", "x")))
                .andExpect(status().isOk());
        shiftToClubDay(3, 9);
        backfill(manager, horse, day0.plusDays(1).toString(), "sau khi bán").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
        assertThat(balance(id)).isEqualTo(10);
        TestClock.reset();
    }
}
