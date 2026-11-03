package vn.racehorse.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The daily stable and care fee, once per managed day, with catch-up (BR-178, BR-179, TECH-167, AC-353). */
class DailyFeeTest extends BillingTestBase {

    @Test
    void AC_353_threeManagedDaysAreChargedOnceEachAndASecondRunAddsNothing() throws Exception {
        BigDecimal price = ensureDailyFee();
        LocalDate d0 = enterFeeWorld();
        long horse = newHorse();
        Set<YearMonth> months = monthsAround(d0);
        assertThat(runFeeAt(d0, 3)).isEqualTo(3);
        var fees = horseLines(manager, horse, "DAILY_FEE", months);
        assertThat(fees).hasSize(3);
        assertThat(fees).allSatisfy(l -> assertThat(l.get("amount").decimalValue()).isEqualByComparingTo(price));
        assertThat(dailyFee.run()).isZero();
        assertThat(horseLines(manager, horse, "DAILY_FEE", months)).hasSize(3);
    }

    @Test
    void anArchivedHorseIsNotChargedFromItsArchiveDay() throws Exception {
        ensureDailyFee();
        LocalDate d0 = enterFeeWorld();
        long horse = newHorse();
        runFeeAt(d0, 1);
        asJson(manager, post("/api/horses/" + horse + "/archive"), Map.of("reason", "RETIRED", "note", "nghỉ hưu"))
                .andExpect(status().isOk());
        runFeeAt(d0, 4);
        // Day d0 was charged before the archive, the archive day and the days after are not.
        assertThat(horseLines(manager, horse, "DAILY_FEE", monthsAround(d0))).hasSize(1);
    }

    @Test
    void aMissedDayIsCreatedByTheNextRun() throws Exception {
        ensureDailyFee();
        LocalDate d0 = enterFeeWorld();
        long horse = newHorse();
        assertThat(runFeeAt(d0, 1)).isEqualTo(1);
        // Nothing ran for two days; the next run catches up.
        assertThat(runFeeAt(d0, 4)).isEqualTo(3);
        assertThat(horseLines(manager, horse, "DAILY_FEE", monthsAround(d0))).hasSize(4);
    }

    @Test
    void aHorseRegisteredLaterIsNotChargedForEarlierDays() throws Exception {
        ensureDailyFee();
        LocalDate d0 = enterFeeWorld();
        runFeeAt(d0, 2);
        shiftToClubDay((int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(CLUB), d0.plusDays(2)), 11);
        long horse = newHorse();
        runFeeAt(d0, 5);
        // Registered on day d0+2: charged for d0+2, d0+3, d0+4 only.
        assertThat(horseLines(manager, horse, "DAILY_FEE", monthsAround(d0))).hasSize(3);
    }

    @Test
    void aDayBeforeTheFirstPriceHasNoChargeAndNoRunYet() throws Exception {
        // The first daily price is never in the past, so history is not charged when billing goes live.
        ensureDailyFee();
        LocalDate first = jdbc.queryForObject("SELECT min(effective_from) FROM service_price WHERE kind = 'DAILY_FEE'",
                LocalDate.class);
        assertThat(first).isAfterOrEqualTo(LocalDate.now(CLUB).minusDays(400));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_day_run WHERE day < ?", Integer.class, first))
                .isZero();
    }
}
