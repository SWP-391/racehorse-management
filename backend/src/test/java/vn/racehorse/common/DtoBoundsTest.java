package vn.racehorse.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import vn.racehorse.stable.controller.SupplyController;
import vn.racehorse.stock.controller.StockController;
import vn.racehorse.training.service.PlanService;
import vn.racehorse.training.controller.TrainingController;
import vn.racehorse.veterinary.controller.VeterinaryController;
import vn.racehorse.stable.dto.ProposalRequest;
import vn.racehorse.stock.dto.PriceRequest;
import vn.racehorse.training.dto.EndRequest;
import vn.racehorse.training.dto.PlanPhaseInput;
import vn.racehorse.training.dto.PlanRequest;
import vn.racehorse.veterinary.dto.DueChangeRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Plausibility bounds and the date window on the request DTOs (input-validation, TECH-190); the wording is the client's. */
class DtoBoundsTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Map<String, String> refused(Object dto) {
        Map<String, String> out = new TreeMap<>();
        for (ConstraintViolation<Object> v : VALIDATOR.validate(dto)) {
            out.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return out;
    }

    private static EndRequest end(Integer distance, Integer duration, Integer heartRate,
                                                      String speed, String weight) {
        return new EndRequest(distance, duration, null, null, null, heartRate,
                speed == null ? null : new BigDecimal(speed), weight == null ? null : new BigDecimal(weight), null, null,
                null);
    }

    @Test
    void indicatorsInsideTheBoundsPass() {
        assertThat(refused(end(2400, 45, 150, "62.5", "480"))).isEmpty();
        assertThat(refused(end(null, null, null, null, null))).isEmpty();
    }

    @Test
    void indicatorsOutsideTheBoundsAreNamedWithTheClientsWording() {
        assertThat(refused(end(50_001, 601, 301, "100.1", "1501"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "distanceM", "Giá trị tối đa là 50000.",
                "durationMin", "Giá trị tối đa là 600.",
                "avgHeartRate", "Giá trị tối đa là 300.",
                "maxSpeed", "Giá trị tối đa là 100.",
                "weightKg", "Giá trị tối đa là 1500."));
        assertThat(refused(end(null, null, 19, null, "99"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "avgHeartRate", "Giá trị tối thiểu là 20.",
                "weightKg", "Giá trị tối thiểu là 100."));
    }

    @Test
    void aDateOutsideTheClubWindowIsRefused() {
        var early = new DueChangeRequest(LocalDate.of(202, 5, 1), null);
        var late = new DueChangeRequest(LocalDate.of(20255, 5, 1), null);
        var fine = new DueChangeRequest(LocalDate.of(2026, 5, 1), null);
        assertThat(refused(early)).containsEntry("newDue", "Ngày không được trước 01/01/1990.");
        assertThat(refused(late)).containsEntry("newDue", "Ngày không được sau 31/12/2100.");
        assertThat(refused(fine)).isEmpty();
    }

    @Test
    void phasesAreCheckedInsideThePlan() {
        var phase = new PlanPhaseInput("Nền", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), null, 60_000,
                new BigDecimal("1200"), null);
        var plan = new PlanRequest("Kế hoạch", java.util.List.of(phase));
        assertThat(refused(plan)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "phases[0].distanceM", "Giá trị tối đa là 50000.",
                "phases[0].weeklyVolumeKm", "Giá trị tối đa là 1000."));
    }

    @Test
    void stockAndSupplyNumbersAreCapped() {
        assertThat(refused(new PriceRequest(new BigDecimal("1000000001"))))
                .containsEntry("price", "Giá trị tối đa là 1000000000.");
        assertThat(refused(new ProposalRequest(new BigDecimal("1000001"), null)))
                .containsEntry("quantity", "Giá trị tối đa là 1000000.");
        assertThat(refused(new PriceRequest(new BigDecimal("25000")))).isEmpty();
    }
}
