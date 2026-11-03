package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The fee type chosen with an examination and its charge (BR-060, BR-178, BR-179, AC-360, AC-076, AC-079, AC-354). */
class ExaminationFeeTest extends BillingTestBase {

    @Test
    void AC_360_theVeterinarianChoosesATypeButNeverSeesAnAmount() throws Exception {
        long type = feeType(200000);
        JsonNode options = body(as(vet, get("/api/exam-fee-types")).andExpect(status().isOk()));
        boolean listed = false;
        for (JsonNode o : options) {
            listed |= o.get("id").asLong() == type;
            assertThat(o.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name");
        }
        assertThat(listed).isTrue();
        long horse = newHorse();
        JsonNode saved = body(recordExam(horse, type).andExpect(status().isCreated()));
        assertThat(saved.toString()).doesNotContain("200000").doesNotContain("fee");
        assertThat(body(as(vet, get("/api/horses/" + horse + "/examinations"))).toString()).doesNotContain("200000");
        as(vet, get("/api/billing/prices")).andExpect(status().isForbidden());
        costsOf(vet, horse, YearMonth.from(today())).andExpect(status().isForbidden());
    }

    @Test
    void theChargeUsesThePriceOfTheExaminationDateAndGoesToTheOwner() throws Exception {
        long type = feeType(200000);
        long horse = newHorse();
        recordExam(horse, type).andExpect(status().isCreated());
        JsonNode statement = statementOf(owner, YearMonth.from(today()));
        var lines = lines(statement, "EXAM");
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).get("amount").decimalValue()).isEqualByComparingTo("200000");
        assertThat(lines.get(0).get("description").asText()).startsWith("Khám: ");
        // A later price change never reaches the examination already recorded (AC-354).
        setPrice("EXAM_FEE", type, 500000, today().plusDays(1)).andExpect(status().isCreated());
        assertThat(lines(statementOf(owner, YearMonth.from(today())), "EXAM").get(0).get("amount").decimalValue())
                .isEqualByComparingTo("200000");
    }

    @Test
    void anExaminationIsStillSavedWhenNoFeeTypeExists() throws Exception {
        long horse = newHorse();
        // No priced type of this test exists; types of other tests are deactivated after them.
        if (body(as(vet, get("/api/exam-fee-types"))).isEmpty()) {
            recordExam(horse, null).andExpect(status().isCreated());
            assertThat(lines(statementOf(owner, YearMonth.from(today())), "EXAM")).isEmpty();
        }
    }

    @Test
    void theTypeIsRequiredOnceAPricedTypeExists() throws Exception {
        feeType(150000);
        long horse = newHorse();
        recordExam(horse, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.feeTypeId").exists());
        recordExam(horse, 999999L).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.feeTypeId").exists());
        assertThat(body(as(vet, get("/api/horses/" + horse + "/examinations")))).isEmpty();
    }

    @Test
    void aTypeWithNoPriceOnTheExaminationDateIsRefusedAndNothingIsSaved() throws Exception {
        // A fee type is always created with a first price (AC-453); this one only starts in five days.
        long id = body(asJson(manager, post("/api/billing/fee-types"),
                java.util.Map.of("name", unique("Chưa giá"), "amount", 100000, "effectiveFrom", today().plusDays(5).toString()))
                .andExpect(status().isCreated())).get("id").asLong();
        long horse = newHorse();
        recordExam(horse, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_PRICE_FOR_DATE"));
        assertThat(body(as(vet, get("/api/horses/" + horse + "/examinations")))).isEmpty();
        asJson(manager, post("/api/billing/fee-types/" + id + "/deactivate"), java.util.Map.of());
    }

    @Test
    void AC_079_aGroomStillCannotRecordAnExamination() throws Exception {
        long type = feeType(100000);
        long horse = newHorse();
        asJson(account(Role.GROOM), post("/api/horses/" + horse + "/examinations"), exam("x", type))
                .andExpect(status().isForbidden());
        as(account(Role.GROOM), get("/api/exam-fee-types")).andExpect(status().isForbidden());
    }
}
