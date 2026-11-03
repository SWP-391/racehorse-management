package vn.racehorse.billing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import vn.racehorse.billing.dto.ChargeSpec;
import vn.racehorse.billing.entity.Charge;
import vn.racehorse.billing.service.ChargeService;

/** Rounding, idempotency and the closed-month redirect of the charge core (BR-179, TECH-165, AC-353). */
class ChargeCoreTest extends BillingTestBase {

    @Autowired
    private ChargeService chargeService;

    private boolean record(long horse, LocalDate day, String key, String quantity, String unitPrice) {
        AtomicBoolean created = new AtomicBoolean();
        tx.executeWithoutResult(s -> created.set(chargeService.record(new ChargeSpec(horse, null,
                Charge.Category.EXAM, day, "Khám thử", new BigDecimal(quantity), new BigDecimal(unitPrice), null, key,
                key, null, null))));
        return created.get();
    }

    @Test
    void theSameFactTwiceGivesOneCharge() throws Exception {
        long horse = newHorse();
        String key = "CORE:once:" + horse;
        assertThat(record(horse, today(), key, "1", "1000")).isTrue();
        assertThat(record(horse, today(), key, "1", "1000")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM charge WHERE idempotency_key = ?", Integer.class, key))
                .isEqualTo(1);
    }

    @Test
    void eachLineIsRoundedHalfUpToWholeDongSoTheTotalEqualsTheLines() throws Exception {
        long horse = newHorse();
        record(horse, today(), "CORE:r1:" + horse, "0.5", "12001");
        record(horse, today(), "CORE:r2:" + horse, "3", "1000.50");
        var lines = lines(statementOf(owner, YearMonth.from(today())), "EXAM");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("amount").decimalValue()).isEqualByComparingTo("6001");
        assertThat(lines.get(1).get("amount").decimalValue()).isEqualByComparingTo("3002");
        assertThat(statementOf(owner, YearMonth.from(today())).get("total").decimalValue()).isEqualByComparingTo("9003");
    }

    @Test
    void workDatedInAClosedMonthLandsInTheFirstOpenMonthAndKeepsItsWorkDate() throws Exception {
        long horse = newHorse();
        YearMonth closed = YearMonth.of(1948, 2);
        as(manager, post("/api/billing/months/" + closed + "/close")).andExpect(status().isOk());
        LocalDate work = closed.atDay(12);
        assertThat(record(horse, work, "CORE:closed:" + horse, "1", "5000")).isTrue();
        assertThat(jdbc.queryForObject("SELECT charge_date FROM charge WHERE idempotency_key = ?", LocalDate.class,
                "CORE:closed:" + horse)).isEqualTo(closed.plusMonths(1).atDay(1));
        assertThat(jdbc.queryForObject("SELECT work_date FROM charge WHERE idempotency_key = ?", LocalDate.class,
                "CORE:closed:" + horse)).isEqualTo(work);
        // Close the month it landed in too, so no earlier month stays open for the other tests.
        as(manager, post("/api/billing/months/" + closed.plusMonths(1) + "/close")).andExpect(status().isOk());
    }
}
