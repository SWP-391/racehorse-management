package vn.racehorse.stock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import vn.racehorse.stock.dto.StockLine;
import vn.racehorse.stock.exception.InsufficientStockException;
import vn.racehorse.stock.service.StockService;
import vn.racehorse.stock.service.SupplyUsed;

/** The stock ledger: prices, receipts, adjustments, immutability and concurrency (BR-171, AC-331, AC-332, AC-338). */
class StockLedgerTest extends StockTestBase {

    @Autowired
    StockService stockService;

    @Test
    void AC_331_theLedgerAddsUpToTheQuantityAndThePriceHistoryIsKept() throws Exception {
        JsonNode item = supply("FEED", 25000, 120);
        long id = item.get("id").asLong();
        assertThat(item.get("unitPrice").decimalValue()).isEqualByComparingTo("25000");
        JsonNode first = movements(id).get(0);
        assertThat(first.get("kind").asText()).isEqualTo("RECEIPT");
        assertThat(first.get("delta").asLong()).isEqualTo(120);
        assertThat(first.get("balanceAfter").asLong()).isEqualTo(120);

        receive(id, 30).andExpect(status().isCreated());
        asJson(manager, post("/api/supplies/" + id + "/adjustments"), Map.of("newQuantity", 140, "reason", "kiểm kê"))
                .andExpect(status().isCreated());
        assertThat(balance(id)).isEqualTo(140);
        assertThat(ledgerSum(id)).isEqualTo(140);

        // A later price applies to later lines only; the earlier line keeps the price it was made at.
        asJson(manager, put("/api/supplies/" + id + "/price"), Map.of("price", 30000)).andExpect(status().isOk());
        receive(id, 10).andExpect(status().isCreated());
        JsonNode lines = movements(id);
        assertThat(lines.get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("30000");
        assertThat(lines.get(lines.size() - 1).get("unitPrice").decimalValue()).isEqualByComparingTo("25000");
        assertThat(auditActions(manager)).contains("STOCK_RECEIVED", "STOCK_ADJUSTED", "SUPPLY_PRICE_SET");
    }

    @Test
    void AC_332_anAdjustmentBelowZeroOrWithoutAReasonIsRefused() throws Exception {
        long id = supply("FEED", 1000, 10).get("id").asLong();
        asJson(manager, post("/api/supplies/" + id + "/adjustments"), Map.of("newQuantity", 5))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.reason").exists());
        asJson(manager, post("/api/supplies/" + id + "/adjustments"), Map.of("delta", -11, "reason", "hỏng"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NEGATIVE_STOCK"));
        asJson(manager, post("/api/supplies/" + id + "/adjustments"), Map.of("newQuantity", -1, "reason", "x"))
                .andExpect(status().isBadRequest());
        asJson(manager, post("/api/supplies/" + id + "/adjustments"), Map.of("newQuantity", 1.5, "reason", "x"))
                .andExpect(status().isBadRequest());
        assertThat(balance(id)).isEqualTo(10);
        asJson(manager, post("/api/supplies/" + id + "/adjustments"), Map.of("delta", -4, "reason", "hỏng"))
                .andExpect(status().isCreated());
        assertThat(balance(id)).isEqualTo(6);
        receive(id, 0).andExpect(status().isBadRequest());
        receive(id, -2).andExpect(status().isBadRequest());
    }

    @Test
    void AC_338_onlyTheClubManagerSetsPricesReceivesAndAdjusts() throws Exception {
        long id = supply("MEDICINE", 1000, 10).get("id").asLong();
        var groom = defaultGroom();
        for (var who : List.of(vet, trainer, owner, groom)) {
            asJson(who, put("/api/supplies/" + id + "/price"), Map.of("price", 5)).andExpect(status().isForbidden());
            asJson(who, post("/api/supplies/" + id + "/receipts"), Map.of("quantity", 5)).andExpect(status().isForbidden());
            asJson(who, post("/api/supplies/" + id + "/adjustments"), Map.of("newQuantity", 1, "reason", "x"))
                    .andExpect(status().isForbidden());
            as(who, get("/api/supplies/" + id + "/movements")).andExpect(status().isForbidden());
        }
        as(vet, get("/api/supplies")).andExpect(status().isForbidden());
        assertThat(balance(id)).isEqualTo(10);
    }

    @Test
    void theGroomSeesTheCatalogWithoutQuantityOrPrice() throws Exception {
        JsonNode item = supply("FEED", 25000, 50);
        JsonNode seen = null;
        for (JsonNode i : body(as(defaultGroom(), get("/api/supplies")).andExpect(status().isOk()))) {
            if (i.get("id").asLong() == item.get("id").asLong()) {
                seen = i;
            }
        }
        assertThat(seen).isNotNull();
        assertThat(seen.has("quantity")).isFalse();
        assertThat(seen.has("unitPrice")).isFalse();
    }

    @Test
    void theLastUnitGoesToExactlyOneOfTwoWriters() throws Exception {
        long id = supply("FEED", 1000, 1).get("id").asLong();
        long horse = newHorse();
        AtomicInteger refused = new AtomicInteger();
        Callable<Boolean> use = () -> {
            try {
                tx.executeWithoutResult(s -> stockService.use(horse, List.of(new StockLine(id, 1)),
                        SupplyUsed.Source.FEED, "TEST", 1L, today(), null));
                return true;
            } catch (InsufficientStockException e) {
                refused.incrementAndGet();
                return false;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = pool.submit(use);
            Future<Boolean> b = pool.submit(use);
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(refused.get()).isEqualTo(1);
        assertThat(balance(id)).isZero();
        assertThat(ledgerSum(id)).isZero();
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void aLedgerLineAndAPriceRowCannotBeChangedOrDeleted() throws Exception {
        long id = supply("FEED", 1000, 5).get("id").asLong();
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update stock_movement set delta = 0 where supply_item_id = ?", id));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("delete from stock_movement where supply_item_id = ?", id));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update supply_price set price = 1 where supply_item_id = ?", id));
        assertThat(ledgerSum(id)).isEqualTo(5);
    }

    @Test
    void theMovementsPageTwentyAtATime_andTheTotalIsKept() throws Exception {
        long id = supply("FEED", 1000, 10).get("id").asLong();
        for (int i = 0; i < 24; i++) {
            receive(id, 1).andExpect(status().isCreated());
        }
        JsonNode first = pageBody(as(manager, get("/api/supplies/" + id + "/movements").param("size", "20")).andExpect(status().isOk()));
        assertThat(first.get("items").size()).isEqualTo(20);
        assertThat(first.get("total").asLong()).isEqualTo(25);
        JsonNode second = pageBody(as(manager, get("/api/supplies/" + id + "/movements").param("size", "20").param("page", "1")).andExpect(status().isOk()));
        assertThat(second.get("items").size()).isEqualTo(5);
        as(manager, get("/api/supplies/" + id + "/movements").param("size", "0")).andExpect(status().isBadRequest());
    }
}
