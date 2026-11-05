package vn.racehorse.veterinary;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.training.TrainingTestBase;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Examination records, who may read them, and the first-examination effects (BR-009, BR-060, BR-095). */
class ExaminationsTest extends TrainingTestBase {

    private Map<String, Object> exam(String diagnosis, Object weight) {
        Map<String, Object> m = new HashMap<>();
        m.put("diagnosis", diagnosis);
        m.put("bodyRegion", "LF_FETLOCK");
        m.put("weightKg", weight);
        m.put("treatment", "Chườm lạnh, nghỉ 3 ngày");
        m.put("prescription", "Phenylbutazone");
        return m;
    }

    @Test
    void AC_076_examinationSavedWithTodaysDate() throws Exception {
        long horse = newHorse();
        JsonNode e = body(asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("Viêm gân chân trước", 482.5))
                .andExpect(status().isCreated()));
        assertThat(e.get("examDate").asText()).isEqualTo(today().toString());
        assertThat(e.get("bodyRegion").asText()).isEqualTo("LF_FETLOCK");
        assertThat(e.get("weightKg").asDouble()).isEqualTo(482.5);
        assertThat(e.get("treatment").asText()).contains("Chườm lạnh");
        assertThat(e.get("prescription").asText()).isEqualTo("Phenylbutazone");
        assertThat(e.get("recordedBy").asText()).isEqualTo(vet.displayName());
        JsonNode history = body(as(vet, get("/api/horses/" + horse + "/examinations")).andExpect(status().isOk()));
        assertThat(history.size()).isEqualTo(1);
    }

    @Test
    void AC_078_emptyDiagnosisRefused() throws Exception {
        long horse = newHorse();
        asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("  ", 480))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.diagnosis").exists());
        assertThat(body(as(vet, get("/api/horses/" + horse + "/examinations"))).size()).isZero();
    }

    @Test
    void AC_079_groomCannotRecordAnExamination() throws Exception {
        long horse = newHorse();
        asJson(account(Role.GROOM), post("/api/horses/" + horse + "/examinations"), exam("x", 400))
                .andExpect(status().isForbidden());
    }

    @Test
    void AC_117_headTrainerReadsExaminationDetailsReadOnly() throws Exception {
        long horse = newHorse();
        asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("Sưng khớp gối", 470)).andExpect(status().isCreated());
        JsonNode history = body(as(trainer, get("/api/horses/" + horse + "/examinations")).andExpect(status().isOk()));
        JsonNode e = history.get(0);
        assertThat(e.get("diagnosis").asText()).isEqualTo("Sưng khớp gối");
        assertThat(e.get("bodyRegion").asText()).isEqualTo("LF_FETLOCK");
        assertThat(e.get("treatment").asText()).isNotBlank();
        assertThat(e.get("prescription").asText()).isNotBlank();
        asJson(trainer, post("/api/horses/" + horse + "/examinations"), exam("sửa", 470)).andExpect(status().isForbidden());
    }

    @Test
    void AC_118_clubManagerRefusedExaminationDetails() throws Exception {
        long horse = newHorse();
        asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("Chẩn đoán", 470)).andExpect(status().isCreated());
        as(manager, get("/api/horses/" + horse + "/examinations")).andExpect(status().isForbidden());
        as(owner, get("/api/horses/" + horse + "/examinations")).andExpect(status().isForbidden());
    }

    @Test
    void AC_077_firstExaminationClearsLabelButIntakeLockRemains() throws Exception {
        long horse = newHorse();
        assertThat(health(horse).get("newUnexamined").asBoolean()).isTrue();
        asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("Khám nhập đàn", 480)).andExpect(status().isCreated());
        JsonNode h = health(horse);
        assertThat(h.get("newUnexamined").asBoolean()).isFalse();
        assertThat(h.get("lock").get("kind").asText()).isEqualTo("INTAKE");
        assertThat(h.get("status").asText()).isEqualTo("UNDER_OBSERVATION");
    }

    @Test
    void AC_067_boardLabelDisappearsAfterTheFirstExamination() throws Exception {
        long horse = newHorse();
        assertThat(boardLabel(horse)).isTrue();
        asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("Khám nhập đàn", 480)).andExpect(status().isCreated());
        assertThat(boardLabel(horse)).isFalse();
    }

    private boolean boardLabel(long horse) throws Exception {
        for (JsonNode r : body(as(trainer, get("/api/progress")).andExpect(status().isOk()))) {
            if (r.get("horseId").asLong() == horse) {
                return r.get("newUnexamined").asBoolean();
            }
        }
        throw new AssertionError("horse not on board");
    }

    @Test
    void AC_086_afterTheIntakeLockIsLiftedThePlanCanBeActivatedAndScheduled() throws Exception {
        long horse = newHorse();
        long plan = draftPlan(horse);
        asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("Khám nhập đàn", 480)).andExpect(status().isCreated());
        asJson(vet, post("/api/horses/" + horse + "/lock/lift"), Map.of("returnLevel", "RESUME_AS_IS")).andExpect(status().isOk());
        as(trainer, post("/api/plans/" + plan + "/activate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));
        asJson(trainer, post("/api/plans/" + plan + "/sessions"), sessionBody(today().plusDays(1), "TROT", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SCHEDULED"));
    }

    // ---- Owner weight view (BR-081) ---------------------------------------------------------------------

    @Test
    void AC_107_ownerSeesTheThreeWeightsOverTime() throws Exception {
        long horse = newHorse();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> e = exam("Khám định kỳ " + i, 470 + i * 5);
            e.put("examDate", today().minusDays(20 - i * 7).toString());
            asJson(vet, post("/api/horses/" + horse + "/examinations"), e).andExpect(status().isCreated());
        }
        JsonNode weights = body(as(owner, get("/api/horses/" + horse + "/weights")).andExpect(status().isOk()));
        assertThat(weights.size()).isEqualTo(3);
        assertThat(weights.get(0).get("weightKg").asDouble()).isEqualTo(470.0);
        assertThat(weights.get(2).get("weightKg").asDouble()).isEqualTo(480.0);
        assertThat(weights.get(0).get("date").asText()).isLessThan(weights.get(2).get("date").asText());
    }

    @Test
    void AC_108_activityStatusIsNeverShownToTheOwner() throws Exception {
        long horse = eligibleHorse();
        asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("Khám", 475)).andExpect(status().isCreated());
        long session = schedule(activePlan(horse), today());
        start(session, false);
        assertThat(health(horse).get("activity").asText()).isEqualTo("TRAINING");
        String ownerView = as(owner, get("/api/horses/" + horse + "/weights")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ownerView).doesNotContain("activity").doesNotContain("TRAINING").doesNotContain("diagnosis");
        as(owner, get("/api/horses/" + horse + "/health")).andExpect(status().isForbidden());
    }

    @Test
    void AC_109_otherOwnersHorseRefused() throws Exception {
        long horse = newHorse();
        as(account(Role.HORSE_OWNER), get("/api/horses/" + horse + "/weights")).andExpect(status().isForbidden());
    }

    @Test
    void theExaminationListPagesTwentyAtATime() throws Exception {
        long horse = newHorse();
        for (int i = 0; i < 21; i++) {
            asJson(vet, post("/api/horses/" + horse + "/examinations"), exam("Khám lần " + i, 480)).andExpect(status().isCreated());
        }
        JsonNode first = pageBody(as(vet, get("/api/horses/" + horse + "/examinations").param("size", "20")).andExpect(status().isOk()));
        assertThat(first.get("items").size()).isEqualTo(20);
        assertThat(first.get("total").asLong()).isEqualTo(21);
        JsonNode second = pageBody(as(vet, get("/api/horses/" + horse + "/examinations").param("size", "20").param("page", "1")).andExpect(status().isOk()));
        assertThat(second.get("items").size()).isEqualTo(1);
    }
}
