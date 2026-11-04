package vn.racehorse.horse.service;

import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.horse.entity.Horse;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Who is the effective trainer on a given day: the temporary one before the return date, else the main one (BR-156). */
class HorseTrainerServiceTest {

    private final HorseTrainerService service = new HorseTrainerService(null, null, null, null, null);
    private final UserAccount main = new UserAccount("main", "Main", "x", Role.HEAD_TRAINER, Instant.now());
    private final UserAccount temp = new UserAccount("temp", "Temp", "x", Role.HEAD_TRAINER, Instant.now());
    private final LocalDate day = LocalDate.of(2026, 11, 10);

    private Horse horse() {
        Horse horse = new Horse(Instant.now());
        horse.setMainTrainer(main, Instant.now());
        return horse;
    }

    @Test
    void noLoanMeansTheMainTrainer() {
        assertThat(service.effectiveTrainer(horse(), day)).isSameAs(main);
    }

    @Test
    void theLoanHoldsBeforeTheReturnDateAndEndsOnIt() {
        Horse horse = horse();
        horse.lendTo(temp, day, Instant.now());
        assertThat(service.effectiveTrainer(horse, day.minusDays(2))).isSameAs(temp);
        assertThat(service.effectiveTrainer(horse, day.minusDays(1))).isSameAs(temp);
        assertThat(service.effectiveTrainer(horse, day)).isSameAs(main);
        assertThat(service.effectiveTrainer(horse, day.plusDays(1))).isSameAs(main);
    }

    @Test
    void aHorseWithoutAnyTrainerHasNone() {
        Horse horse = new Horse(Instant.now());
        assertThat(service.effectiveTrainer(horse, day)).isNull();
        assertThat(service.canWrite(horse, null)).isFalse();
    }
}
