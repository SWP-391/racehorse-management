package vn.racehorse.injury;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Injury marks by body region, their recovery history, who may see them, and that they leave health alone (REQ-014). */
class InjuryMarksTest extends TrainingTestBase {

    private long exam(long horse) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("diagnosis", "Viêm khớp bàn");
        m.put("bodyRegion", "LF_FETLOCK");
        return body(asJson(vet, post("/api/horses/" + horse + "/examinations"), m).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private JsonNode mark(long horse, String region, long exam) throws Exception {
        return body(asJson(vet, post("/api/horses/" + horse + "/injury-marks"),
                Map.of("regionCode", region, "examinationId", exam)).andExpect(status().isCreated()));
    }

    private JsonNode setStatus(long mark, String status, String note) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("note", note);
        return body(asJson(vet, post("/api/injury-marks/" + mark + "/status"), m).andExpect(status().isOk()));
    }

    private JsonNode marks(AccountPrincipal who, long horse) throws Exception {
        return body(as(who, get("/api/horses/" + horse + "/injury-marks")).andExpect(status().isOk()));
    }

    @Test
    void AC_126_markOnLeftForeFetlockSavedAsNew() throws Exception {
        long horse = newHorse();
        long exam = exam(horse);
        JsonNode m = mark(horse, "LF_FETLOCK", exam);
        assertThat(m.get("regionCode").asText()).isEqualTo("LF_FETLOCK");
        assertThat(m.get("status").asText()).isEqualTo("NEW");
        assertThat(m.get("createdBy").asText()).isEqualTo(vet.displayName());
        assertThat(m.get("history").get(0).get("date").asText()).isEqualTo(today().toString());
        assertThat(marks(vet, horse).get(0).get("id").asLong()).isEqualTo(m.get("id").asLong());
    }

    @Test
    void AC_128_twoMarksOnOneExaminationListedSeparately() throws Exception {
        long horse = newHorse();
        long exam = exam(horse);
        mark(horse, "LF_FETLOCK", exam);
        mark(horse, "RH_HOCK", exam);
        JsonNode list = marks(vet, horse);
        assertThat(list.size()).isEqualTo(2);
        assertThat(StreamSupport.stream(list.spliterator(), false).map(n -> n.get("regionCode").asText()))
                .containsExactlyInAnyOrder("LF_FETLOCK", "RH_HOCK");
    }

    @Test
    void AC_129_examinationIsRequiredAndMustBelongToTheHorse() throws Exception {
        long horse = newHorse();
        Map<String, Object> noExam = new HashMap<>();
        noExam.put("regionCode", "LF_FETLOCK");
        asJson(vet, post("/api/horses/" + horse + "/injury-marks"), noExam)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.examinationId").exists());
        long other = newHorse();
        asJson(vet, post("/api/horses/" + horse + "/injury-marks"),
                Map.of("regionCode", "LF_FETLOCK", "examinationId", exam(other)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.examinationId").exists());
        assertThat(marks(vet, horse).size()).isZero();
    }

    @Test
    void AC_130_unknownRegionRefused() throws Exception {
        long horse = newHorse();
        asJson(vet, post("/api/horses/" + horse + "/injury-marks"),
                Map.of("regionCode", "LEFT_ANTENNA", "examinationId", exam(horse)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.regionCode").exists());
        assertThat(marks(vet, horse).size()).isZero();
    }

    @Test
    void AC_131_archivedHorseRefused() throws Exception {
        long horse = newHorse();
        long exam = exam(horse);
        asJson(manager, post("/api/horses/" + horse + "/archive"), Map.of("reason", "RETIRED")).andExpect(status().isOk());
        asJson(vet, post("/api/horses/" + horse + "/injury-marks"), Map.of("regionCode", "LF_FETLOCK", "examinationId", exam))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
    }

    @Test
    void AC_132_headTrainerCannotCreate() throws Exception {
        long horse = newHorse();
        long exam = exam(horse);
        asJson(trainer, post("/api/horses/" + horse + "/injury-marks"), Map.of("regionCode", "LF_FETLOCK", "examinationId", exam))
                .andExpect(status().isForbidden());
        assertThat(marks(vet, horse).size()).isZero();
    }

    @Test
    void AC_133_healthStatusAndLockUnchangedAndSessionsStillSchedulable() throws Exception {
        long horse = eligibleHorse();
        JsonNode before = health(horse);
        long plan = activePlan(horse);
        mark(horse, "RH_HOCK", exam(horse));
        JsonNode after = health(horse);
        assertThat(after.get("status")).isEqualTo(before.get("status"));
        assertThat(after.get("lock")).isEqualTo(before.get("lock"));
        assertThat(schedule(plan, today().plusDays(1))).isPositive();
    }

    @Test
    void AC_135_recoveryBeginsWithNoteInHistory() throws Exception {
        long horse = newHorse();
        long m = mark(horse, "LF_FETLOCK", exam(horse)).get("id").asLong();
        JsonNode updated = setStatus(m, "RECOVERING", "Bắt đầu đi bộ nhẹ");
        assertThat(updated.get("status").asText()).isEqualTo("RECOVERING");
        JsonNode last = updated.get("history").get(updated.get("history").size() - 1);
        assertThat(last.get("to").asText()).isEqualTo("RECOVERING");
        assertThat(last.get("note").asText()).isEqualTo("Bắt đầu đi bộ nhẹ");
        assertThat(last.get("by").asText()).isEqualTo(vet.displayName());
        assertThat(last.get("date").asText()).isEqualTo(today().toString());
    }

    @Test
    void AC_136_historyKeepsEveryStepInOrder() throws Exception {
        long horse = newHorse();
        long m = mark(horse, "LF_FETLOCK", exam(horse)).get("id").asLong();
        setStatus(m, "RECOVERING", "tuần 1");
        JsonNode done = setStatus(m, "RECOVERED", "khỏi hẳn");
        List<String> steps = StreamSupport.stream(done.get("history").spliterator(), false)
                .map(e -> e.get("to").asText()).toList();
        assertThat(steps).containsExactly("NEW", "RECOVERING", "RECOVERED");
        assertThat(done.get("history").get(1).get("note").asText()).isEqualTo("tuần 1");
    }

    @Test
    void AC_137_directRecoveryAccepted() throws Exception {
        long horse = newHorse();
        long m = mark(horse, "LF_FETLOCK", exam(horse)).get("id").asLong();
        assertThat(setStatus(m, "RECOVERED", null).get("status").asText()).isEqualTo("RECOVERED");
    }

    @Test
    void AC_138_recoveredMarkDoesNotReopenAndNewMarkIsSuggested() throws Exception {
        long horse = newHorse();
        long m = mark(horse, "LF_FETLOCK", exam(horse)).get("id").asLong();
        setStatus(m, "RECOVERED", null);
        for (String back : List.of("NEW", "RECOVERING")) {
            asJson(vet, post("/api/injury-marks/" + m + "/status"), Map.of("status", back))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_MARK_TRANSITION"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("tạo dấu mới")));
        }
        assertThat(marks(vet, horse).get(0).get("status").asText()).isEqualTo("RECOVERED");
    }

    @Test
    void AC_139_auditLogShowsCreationAndStatusChange() throws Exception {
        long horse = newHorse();
        long m = mark(horse, "LF_FETLOCK", exam(horse)).get("id").asLong();
        setStatus(m, "RECOVERING", null);
        JsonNode all = body(as(manager, get("/api/audit").param("actorId", vet.id().toString())));
        List<String> actions = StreamSupport.stream(all.spliterator(), false)
                .filter(e -> e.get("targetId").asLong() == horse).map(e -> e.get("action").asText()).toList();
        assertThat(actions).contains("INJURY_MARK_CREATED", "INJURY_MARK_STATUS_CHANGED");
        // The entry carries the catalog name too, so a reader who may not read the catalog still sees a name.
        assertThat(StreamSupport.stream(all.spliterator(), false)
                .filter(e -> e.get("targetId").asLong() == horse && e.get("action").asText().equals("INJURY_MARK_CREATED"))
                .map(e -> e.get("details").asText()).findFirst().orElseThrow()).contains("\"regionName\"");
    }

    @Test
    void AC_140_headTrainerCannotChangeStatus() throws Exception {
        long horse = newHorse();
        long m = mark(horse, "LF_FETLOCK", exam(horse)).get("id").asLong();
        asJson(trainer, post("/api/injury-marks/" + m + "/status"), Map.of("status", "RECOVERING"))
                .andExpect(status().isForbidden());
        assertThat(marks(vet, horse).get(0).get("status").asText()).isEqualTo("NEW");
    }

    @Test
    void AC_141_deletingIsRefusedAndMarkAndHistoryRemain() throws Exception {
        long horse = newHorse();
        long m = mark(horse, "LF_FETLOCK", exam(horse)).get("id").asLong();
        as(vet, delete("/api/injury-marks/" + m)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARK_NOT_DELETABLE"));
        as(trainer, delete("/api/injury-marks/" + m)).andExpect(status().isForbidden());
        JsonNode list = marks(vet, horse);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("history").size()).isEqualTo(1);
    }

    @Test
    void AC_142_headTrainerReadsMarksAndHistoryReadOnly() throws Exception {
        long horse = newHorse();
        long m = mark(horse, "LF_FETLOCK", exam(horse)).get("id").asLong();
        setStatus(m, "RECOVERING", "nhẹ");
        JsonNode list = marks(trainer, horse);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("history").size()).isEqualTo(2);
        as(trainer, get("/api/body-regions")).andExpect(status().isOk());
        asJson(trainer, post("/api/injury-marks/" + m + "/status"), Map.of("status", "RECOVERED"))
                .andExpect(status().isForbidden());
    }

    @Test
    void AC_143_ownerOfTheHorseRefused() throws Exception {
        long horse = newHorse();
        mark(horse, "LF_FETLOCK", exam(horse));
        as(owner, get("/api/horses/" + horse + "/injury-marks")).andExpect(status().isForbidden());
    }

    @Test
    void AC_144_clubManagerRefused() throws Exception {
        long horse = newHorse();
        mark(horse, "LF_FETLOCK", exam(horse));
        as(manager, get("/api/horses/" + horse + "/injury-marks")).andExpect(status().isForbidden());
    }

    @Test
    void AC_145_groomRefused() throws Exception {
        long horse = newHorse();
        mark(horse, "LF_FETLOCK", exam(horse));
        as(account(Role.GROOM), get("/api/horses/" + horse + "/injury-marks")).andExpect(status().isForbidden());
    }

    @Test
    void AC_146_horseWithoutMarksReturnsEmptyList() throws Exception {
        long horse = newHorse();
        assertThat(marks(trainer, horse).size()).isZero();
    }

    @Test
    void catalogListsExactly41DistinctRegions() throws Exception {
        JsonNode regions = body(as(vet, get("/api/body-regions")).andExpect(status().isOk()));
        assertThat(regions.size()).isEqualTo(41);
        assertThat(StreamSupport.stream(regions.spliterator(), false).map(r -> r.get("code").asText()).distinct().count())
                .isEqualTo(41);
        assertThat(regions.toString()).contains("Khớp bàn trước trái");
    }
}
