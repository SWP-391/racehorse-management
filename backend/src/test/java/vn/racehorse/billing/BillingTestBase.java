package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.stock.StockTestBase;
import vn.racehorse.billing.service.DailyFeeService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fixtures for the billing tests. The database is shared by every test, so a fee type is deactivated again after
 * each test (an active priced type makes the examination fee mandatory for the other tests) and the daily fee is
 * exercised in a "far day" world where the day markers before it are filled in.
 */
public abstract class BillingTestBase extends StockTestBase {

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected DailyFeeService dailyFee;

    private final List<Long> feeTypes = new ArrayList<>();

    @AfterEach
    void deactivateFeeTypes() throws Exception {
        TestClock.reset();
        for (Long id : feeTypes) {
            asJson(manager, post("/api/billing/fee-types/" + id + "/deactivate"), Map.of());
        }
        feeTypes.clear();
    }

    /** A new fee type with a price from today, picked by the Veterinarian in the examination form. */
    protected long feeType(long price) throws Exception {
        long id = body(asJson(manager, post("/api/billing/fee-types"),
                Map.of("name", unique("Khám"), "amount", price, "effectiveFrom", today().toString()))
                .andExpect(status().isCreated())).get("id").asLong();
        feeTypes.add(id);
        return id;
    }

    protected ResultActions setPrice(String kind, Long feeTypeId, Object amount, LocalDate from) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("feeTypeId", feeTypeId);
        m.put("amount", amount);
        m.put("effectiveFrom", from.toString());
        return asJson(manager, post("/api/billing/prices"), m);
    }

    /** The daily fee from today at 150000; another test may have set it already, so the actual price is read. */
    protected BigDecimal ensureDailyFee() throws Exception {
        int code = setPrice("DAILY_FEE", null, 150000, today()).andReturn().getResponse().getStatus();
        if (code != 201 && code != 409) {
            throw new AssertionError("daily fee price refused with " + code);
        }
        return body(as(manager, get("/api/billing/prices"))).get("dailyFee").get("currentPrice").decimalValue();
    }

    /**
     * Moves the clock to a club day after every day already processed and marks the days in between as processed,
     * so a run only covers the days of the test. Returns that first day.
     */
    protected LocalDate enterFeeWorld() throws Exception {
        ensureDailyFee();
        LocalDate first = jdbc.queryForObject("SELECT min(effective_from) FROM service_price WHERE kind = 'DAILY_FEE'",
                LocalDate.class);
        LocalDate last = jdbc.queryForObject("SELECT max(day) FROM billing_day_run", LocalDate.class);
        LocalDate from = last == null ? first : last.plusDays(1);
        LocalDate d0 = (last == null ? today() : last).plusDays(4);
        jdbc.update("INSERT INTO billing_day_run (day, processed_at, horses_charged) "
                + "SELECT d::date, now(), 0 FROM generate_series(?::date, ?::date, interval '1 day') d "
                + "ON CONFLICT DO NOTHING", from.toString(), d0.minusDays(1).toString());
        shiftToClubDay((int) java.time.temporal.ChronoUnit.DAYS.between(today(), d0), 10);
        return d0;
    }

    /** Runs the daily fee at 10:00 of the club day {@code days} after {@code d0}; returns days processed. */
    protected int runFeeAt(LocalDate d0, int days) {
        shiftToClubDay((int) java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(CLUB), d0.plusDays(days)), 10);
        return dailyFee.run();
    }

    protected ResultActions costsOf(AccountPrincipal who, long horse, YearMonth month) throws Exception {
        return as(who, get("/api/horses/" + horse + "/costs").param("month", month.toString()));
    }

    /** Lines of one category of a statement, over every horse group. */
    protected List<JsonNode> lines(JsonNode statement, String category) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode h : statement.get("horses")) {
            for (JsonNode c : h.get("categories")) {
                if (c.get("category").asText().equals(category)) {
                    c.get("lines").forEach(out::add);
                }
            }
        }
        return out;
    }

    /** Lines of one category of a horse over the months a test touched. */
    protected List<JsonNode> horseLines(AccountPrincipal who, long horse, String category, Set<YearMonth> months)
            throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (YearMonth m : months) {
            out.addAll(lines(body(costsOf(who, horse, m).andExpect(status().isOk())), category));
        }
        return out;
    }

    protected Set<YearMonth> monthsAround(LocalDate day) {
        Set<YearMonth> m = new LinkedHashSet<>();
        for (int i = -1; i <= 4; i++) {
            m.add(YearMonth.from(day.plusDays(i)));
        }
        return m;
    }

    protected JsonNode statementOf(AccountPrincipal ownerAccount, YearMonth month) throws Exception {
        return body(as(ownerAccount, get("/api/billing/my-statement").param("month", month.toString()))
                .andExpect(status().isOk()));
    }

    protected Map<String, Object> exam(String diagnosis, Long feeTypeId) {
        Map<String, Object> m = new HashMap<>();
        m.put("diagnosis", diagnosis);
        m.put("bodyRegion", "LF_FETLOCK");
        m.put("weightKg", 480);
        if (feeTypeId != null) {
            m.put("feeTypeId", feeTypeId);
        }
        return m;
    }

    protected ResultActions recordExam(long horse, Long feeTypeId) throws Exception {
        return asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("Khám định kỳ", feeTypeId));
    }
}
