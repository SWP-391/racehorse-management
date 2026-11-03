package vn.racehorse.stock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fixtures for the stock tests: supply items with a price, receipts, rations and feeding marks. */
public abstract class StockTestBase extends TrainingTestBase {

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    /** A new active item with a price and an opening quantity, created by the Club Manager. */
    protected JsonNode supply(String category, long price, long quantity) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", unique(category.equals("MEDICINE") ? "Thuốc" : "Cỏ"));
        m.put("category", category);
        m.put("unitId", unitId("kg"));
        m.put("price", price);
        m.put("openingQuantity", quantity);
        return body(asJson(manager, post("/api/supplies"), m).andExpect(status().isCreated()));
    }

    protected JsonNode itemOf(long id) throws Exception {
        for (JsonNode i : body(as(manager, get("/api/supplies")).andExpect(status().isOk()))) {
            if (i.get("id").asLong() == id) {
                return i;
            }
        }
        throw new AssertionError("supply " + id + " not found");
    }

    protected long balance(long id) throws Exception {
        return itemOf(id).get("quantity").asLong();
    }

    protected JsonNode movements(long id) throws Exception {
        return body(as(manager, get("/api/supplies/" + id + "/movements")).andExpect(status().isOk()));
    }

    protected long ledgerSum(long id) throws Exception {
        long sum = 0;
        for (JsonNode m : movements(id)) {
            sum += m.get("delta").asLong();
        }
        return sum;
    }

    protected ResultActions receive(long id, Object quantity) throws Exception {
        return asJson(manager, post("/api/supplies/" + id + "/receipts"), Map.of("quantity", quantity, "note", "nhập hàng"));
    }

    /** A ration of one meal with the given feed items (supply id to quantity per day), composed by the Veterinarian. */
    protected void ration(long horse, Map<Long, Object> daily) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        daily.forEach((id, qty) -> items.add(Map.of("type", "HAY", "supplyItemId", id, "quantity", qty)));
        asJson(vet, put("/api/horses/" + horse + "/ration"),
                Map.of("meals", List.of(Map.of("name", "Bữa sáng", "time", "06:00", "items", items))))
                .andExpect(status().isOk());
    }

    /** A ration of two meals of the same item, for the whole-unit rounding of the daily total. */
    protected void twoMealRation(long horse, long supplyId, Object morning, Object evening) throws Exception {
        asJson(vet, put("/api/horses/" + horse + "/ration"), Map.of("meals", List.of(
                Map.of("name", "Bữa sáng", "time", "06:00", "items", List.of(
                        Map.of("type", "HAY", "supplyItemId", supplyId, "quantity", morning))),
                Map.of("name", "Bữa tối", "time", "18:00", "items", List.of(
                        Map.of("type", "HAY", "supplyItemId", supplyId, "quantity", evening))))))
                .andExpect(status().isOk());
    }

    protected ResultActions markFeeding(AccountPrincipal who, long horse) throws Exception {
        return as(who, post("/api/horses/" + horse + "/chores/FEEDING/done"));
    }

    protected ResultActions backfill(AccountPrincipal who, long horse, String date, String reason) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("horseId", horse);
        m.put("chore", "FEEDING");
        m.put("date", date);
        m.put("reason", reason);
        return asJson(who, post("/api/chores/backfill"), m);
    }

    /** Moves the test clock to the given hour of the club day that is {@code days} days from now. */
    protected LocalDate shiftToClubDay(int days, int hour) {
        ZonedDateTime actual = ZonedDateTime.now(CLUB);
        ZonedDateTime target = actual.toLocalDate().plusDays(days).atTime(LocalTime.of(hour, 0)).atZone(CLUB);
        TestClock.shift(Duration.between(actual, target));
        return target.toLocalDate();
    }

    /** How many notifications of one account contain the text. */
    protected int notificationsContaining(AccountPrincipal who, String text) throws Exception {
        int n = 0;
        for (JsonNode item : body(as(who, get("/api/notifications"))).get("items")) {
            if (item.get("message").asText().contains(text)) {
                n++;
            }
        }
        return n;
    }

    /** The audit actions of one actor, as the Club Manager reads them. */
    protected List<String> auditActions(AccountPrincipal actor) throws Exception {
        List<String> actions = new ArrayList<>();
        for (JsonNode e : body(as(manager, get("/api/audit").param("actorId", actor.id().toString())))) {
            actions.add(e.get("action").asText());
        }
        return actions;
    }
}
