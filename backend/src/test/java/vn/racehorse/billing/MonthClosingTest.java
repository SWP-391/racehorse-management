package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.time.YearMonth;
import java.util.ArrayList;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Closing a month and its immutable invoices (BR-180, AC-356, AC-357). The tests close past months of their own
 * (a year apart) that hold only their own correction lines, so the shared database stays usable for the others.
 */
class MonthClosingTest extends BillingTestBase {

    private static final AtomicInteger YEAR = new AtomicInteger(1990);

    private YearMonth freshMonth() {
        return YearMonth.of(YEAR.incrementAndGet(), 3);
    }

    private void adjustment(long ownerId, YearMonth month, Object amount, String reason) throws Exception {
        asJson(manager, post("/api/billing/adjustments"), Map.of("ownerId", ownerId, "date",
                month.atDay(10).toString(), "amount", amount, "reason", reason)).andExpect(status().isCreated());
    }

    private JsonNode close(YearMonth month) throws Exception {
        return body(as(manager, post("/api/billing/months/" + month + "/close")).andExpect(status().isOk()));
    }

    @Test
    void AC_356_oneInvoicePerOwnerWithTheLinesCopiedAndTheTotal() throws Exception {
        var other = account(Role.HORSE_OWNER);
        var idle = account(Role.HORSE_OWNER);
        YearMonth month = freshMonth();
        adjustment(owner.id(), month, 50000, "phí A");
        adjustment(owner.id(), month, -10000, "giảm trừ B");
        adjustment(other.id(), month, 30000, "phí C");
        JsonNode result = close(month);
        assertThat(result.get("invoices").asInt()).isEqualTo(2);
        assertThat(result.get("total").decimalValue()).isEqualByComparingTo("70000");
        JsonNode mine = statementOf(owner, month);
        assertThat(mine.get("closed").asBoolean()).isTrue();
        assertThat(mine.get("total").decimalValue()).isEqualByComparingTo("40000");
        assertThat(lines(mine, "ADJUSTMENT")).hasSize(2);
        assertThat(mine.get("invoiceNumber").asText()).contains(String.valueOf(owner.id()));
        // An owner without charges gets no invoice, and an owner reads only own invoices.
        assertThat(statementOf(idle, month).get("invoiceId").isNull()).isTrue();
        JsonNode invoices = body(as(owner, get("/api/billing/invoices")).andExpect(status().isOk()));
        assertThat(invoices).hasSize(1);
        as(owner, get("/api/billing/invoices/" + invoices.get(0).get("id").asLong())).andExpect(status().isOk());
        JsonNode theirs = body(as(manager, get("/api/billing/invoices").param("ownerId", other.id().toString())));
        as(owner, get("/api/billing/invoices/" + theirs.get(0).get("id").asLong())).andExpect(status().isForbidden());
        assertThat(auditActions(manager)).contains("BILLING_MONTH_CLOSED");
    }

    @Test
    void aMonthCannotBeClosedTwiceOrBeforeItEnds() throws Exception {
        YearMonth month = freshMonth();
        adjustment(owner.id(), month, 1000, "một");
        close(month);
        as(manager, post("/api/billing/months/" + month + "/close")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTH_ALREADY_CLOSED"));
        as(manager, post("/api/billing/months/" + YearMonth.from(today()) + "/close")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTH_NOT_FINISHED"));
        as(manager, post("/api/billing/months/2026-13/close")).andExpect(status().isBadRequest());
    }

    @Test
    void anEarlierMonthWithUnclosedChargesMustBeClosedFirst() throws Exception {
        YearMonth early = freshMonth();
        YearMonth later = early.plusMonths(2);
        adjustment(owner.id(), early, 1000, "tháng trước");
        adjustment(owner.id(), later, 2000, "tháng sau");
        as(manager, post("/api/billing/months/" + later + "/close")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIOUS_MONTH_OPEN"));
        close(early);
        close(later);
    }

    @Test
    void twoSimultaneousClosingsProduceOneSetOfInvoices() throws Exception {
        YearMonth month = freshMonth();
        adjustment(owner.id(), month, 4000, "đồng thời");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Integer> call = () -> as(manager, post("/api/billing/months/" + month + "/close"))
                        .andReturn().getResponse().getStatus();
                results.add(pool.submit(call));
            }
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : results) {
                codes.add(f.get());
            }
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice WHERE month = ?", Integer.class, month.toString()))
                .isEqualTo(1);
    }

    private JsonNode closing(YearMonth month) throws Exception {
        return body(as(manager, get("/api/billing/months/" + month + "/closing")).andExpect(status().isOk()));
    }

    @Test
    void AC_413_theClosingStateNamesWhyAMonthCannotBeClosed() throws Exception {
        YearMonth early = freshMonth();
        YearMonth later = early.plusMonths(2);
        adjustment(owner.id(), early, 1000, "tháng trước");
        adjustment(owner.id(), later, 2000, "tháng sau");

        assertThat(closing(YearMonth.from(today())).get("state").asText()).isEqualTo("NOT_FINISHED");
        JsonNode blocked = closing(later);
        assertThat(blocked.get("state").asText()).isEqualTo("PREVIOUS_MONTH_OPEN");
        assertThat(blocked.get("openMonth").asText()).isEqualTo(early.toString());
        assertThat(blocked.get("message").asText()).contains(early.toString());
        assertThat(closing(early).get("state").asText()).isEqualTo("READY");

        close(early);
        assertThat(closing(early).get("state").asText()).isEqualTo("CLOSED");
        assertThat(closing(later).get("state").asText()).isEqualTo("READY");
        close(later);
    }

    @Test
    void AC_414_aMonthWithNoChargesCanBeClosedWithNoInvoice() throws Exception {
        YearMonth month = freshMonth();
        assertThat(closing(month).get("state").asText()).isEqualTo("READY");
        JsonNode result = close(month);
        assertThat(result.get("invoices").asInt()).isZero();
        assertThat(result.get("total").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void AC_415_onlyTheClubManagerReadsTheClosingState() throws Exception {
        for (Role role : new Role[]{Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER}) {
            as(account(role), get("/api/billing/months/" + freshMonth() + "/closing")).andExpect(status().isForbidden());
        }
    }

    @Test
    void onlyTheClubManagerMayClose() throws Exception {
        YearMonth month = freshMonth();
        adjustment(owner.id(), month, 1000, "quyền");
        for (Role role : new Role[]{Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER}) {
            as(account(role), post("/api/billing/months/" + month + "/close")).andExpect(status().isForbidden());
        }
        close(month);
    }

    @Test
    void anInvoiceLineSurvivesARenameOfTheHorse() throws Exception {
        YearMonth month = freshMonth();
        long horse = newHorse();
        asJson(manager, post("/api/billing/adjustments"), Map.of("ownerId", owner.id(), "horseId", horse, "date",
                month.atDay(5).toString(), "amount", 7000, "reason", "gắn ngựa")).andExpect(status().isCreated());
        String before = body(as(manager, get("/api/horses/" + horse))).get("name").asText();
        close(month);
        asJson(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/horses/" + horse),
                horseBody("Tên mới " + System.nanoTime() % 1000, "MALE", owner.id())).andExpect(status().isOk());
        assertThat(statementOf(owner, month).get("horses").get(0).get("horseName").asText()).isEqualTo(before);
    }
}
