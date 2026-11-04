package vn.racehorse.billing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.racehorse.account.entity.Role;
import vn.racehorse.billing.dto.ChargeSpec;
import vn.racehorse.billing.entity.Charge;
import vn.racehorse.billing.service.ChargeService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A horse that changes owner charges the earlier owner up to the change (BR-181, AC-358). */
class OwnerPeriodsTest extends BillingTestBase {

    @Autowired
    private ChargeService chargeService;

    private void change(long horse, long newOwner) throws Exception {
        var current = body(as(manager, get("/api/horses/" + horse)).andExpect(status().isOk()));
        Map<String, Object> m = horseBody(current.get("name").asText(), current.get("sex").asText(), newOwner);
        asJson(manager, put("/api/horses/" + horse), m).andExpect(status().isOk());
    }

    private void charge(long horse, LocalDate day, String key) {
        tx.executeWithoutResult(s -> chargeService.record(new ChargeSpec(horse, null,
                Charge.Category.EXAM, day, "Khám", BigDecimal.ONE, new BigDecimal("1000"), null, key, key, null, null)));
    }

    @Test
    void AC_358_chargesBeforeTheChangeBelongToTheEarlierOwnerAndLaterOnesToTheNewOne() throws Exception {
        var second = account(Role.HORSE_OWNER);
        long horse = newHorse();
        charge(horse, today(), "OP:a:" + horse);
        charge(horse, today(), "OP:b:" + horse);
        change(horse, second.id());
        charge(horse, today(), "OP:c:" + horse);
        YearMonth month = YearMonth.from(today());
        var first = costsOf(owner, horse, month).andExpect(status().isOk());
        assertThat(lines(body(first), "EXAM")).hasSize(2);
        assertThat(lines(body(costsOf(second, horse, month).andExpect(status().isOk())), "EXAM")).hasSize(1);
        // The earlier owner keeps reading their own period of a horse they no longer own.
        assertThat(lines(statementOf(owner, month), "EXAM")).hasSize(2);
        assertThat(lines(statementOf(second, month), "EXAM")).hasSize(1);
    }

    @Test
    void aSecondChangeOnTheSameDayLeavesNoEmptyPeriod() throws Exception {
        var second = account(Role.HORSE_OWNER);
        var third = account(Role.HORSE_OWNER);
        long horse = newHorse();
        change(horse, second.id());
        change(horse, third.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horse_owner_period WHERE horse_id = ? AND to_date IS NULL",
                Integer.class, horse)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horse_owner_period WHERE horse_id = ? AND to_date < from_date",
                Integer.class, horse)).isZero();
        charge(horse, today(), "OP:d:" + horse);
        assertThat(lines(statementOf(third, YearMonth.from(today())), "EXAM")).hasSize(1);
        assertThat(lines(statementOf(second, YearMonth.from(today())), "EXAM")).isEmpty();
    }

    @Test
    void aLaterOwnerChangeNeverRewritesEarlierCharges() throws Exception {
        var second = account(Role.HORSE_OWNER);
        long horse = newHorse();
        charge(horse, today(), "OP:e:" + horse);
        change(horse, second.id());
        assertThat(jdbc.queryForObject("SELECT owner_id FROM charge WHERE idempotency_key = ?", Long.class,
                "OP:e:" + horse)).isEqualTo(owner.id());
    }
}
