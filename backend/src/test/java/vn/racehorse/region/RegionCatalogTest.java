package vn.racehorse.region;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One body-region catalog for examinations, incident reports and marks (BR-110, BR-111, BR-060, DEC-036). */
class RegionCatalogTest extends TrainingTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions recordExam(long horse, Map<String, Object> fields) throws Exception {
        Map<String, Object> m = new HashMap<>(fields);
        m.put("diagnosis", "Viêm khớp bàn");
        return asJson(vet, post("/api/horses/" + horse + "/examinations"), m);
    }

    private JsonNode exams(AccountPrincipal who, long horse) throws Exception {
        return body(as(who, get("/api/horses/" + horse + "/examinations")).andExpect(status().isOk()));
    }

    private ResultActions sendIncident(AccountPrincipal groom, long horse, String description, String region)
            throws Exception {
        var request = multipart("/api/incidents").param("horseId", Long.toString(horse)).param("description", description);
        if (region != null) request.param("bodyRegion", region);
        return mvc.perform(request.with(user(groom)).with(csrf()));
    }

    private String catalogName(String code) throws Exception {
        for (JsonNode r : body(as(vet, get("/api/body-regions")).andExpect(status().isOk()))) {
            if (r.get("code").asText().equals(code)) return r.get("vi").asText();
        }
        throw new AssertionError("no region " + code);
    }

    @Test
    void AC_169_catalogRegionWithDetail() throws Exception {
        long horse = newHorse();
        JsonNode e = body(recordExam(horse, Map.of("bodyRegion", "LF_FETLOCK", "bodyDetail", "vết rách 3 cm"))
                .andExpect(status().isCreated()));
        assertThat(e.get("bodyRegion").asText()).isEqualTo("LF_FETLOCK");
        assertThat(e.get("bodyDetail").asText()).isEqualTo("vết rách 3 cm");
        assertThat(e.get("bodyAreaLegacy").isNull()).isTrue();
        assertThat(catalogName("LF_FETLOCK")).isEqualTo("Khớp bàn trước trái");
    }

    @Test
    void AC_170_uncataloguedBodyAreaRefusedAndNamed() throws Exception {
        long horse = newHorse();
        recordExam(horse, Map.of("bodyRegion", "LEFT_WING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.bodyRegion").value(org.hamcrest.Matchers.containsString("Vùng cơ thể")));
        // Free text from an old client is not a catalogued region either.
        recordExam(horse, Map.of("bodyArea", "left foreleg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.bodyRegion").exists());
        // A detail needs a region next to it (P-01).
        recordExam(horse, Map.of("bodyDetail", "vết rách"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.bodyRegion").exists());
        assertThat(exams(vet, horse).size()).isZero();
        // The body area itself stays optional (BR-060).
        recordExam(horse, Map.of()).andExpect(status().isCreated());
    }

    @Test
    void AC_171_oldFreeTextBodyAreaShownUnchanged() throws Exception {
        long horse = newHorse();
        // An examination recorded before the shared catalog (DEC-036): only the free-text column is set.
        jdbc.update("INSERT INTO examination (horse_id, exam_date, diagnosis, body_area, recorded_by_id, recorded_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", horse, java.sql.Date.valueOf(today()), "Sưng chân", "left foreleg",
                vet.id(), java.sql.Timestamp.from(Instant.now()));
        for (AccountPrincipal who : new AccountPrincipal[]{vet, trainer}) {
            JsonNode e = exams(who, horse).get(0);
            assertThat(e.get("bodyAreaLegacy").asText()).isEqualTo("left foreleg");
            assertThat(e.get("bodyRegion").isNull()).isTrue();
        }
    }

    @Test
    void AC_172_incidentWithCatalogRegionAndWithout() throws Exception {
        long horse = eligibleHorse();
        AccountPrincipal groom = account(Role.GROOM);
        JsonNode r = body(sendIncident(groom, horse, "Móng bị xước", "LF_HOOF").andExpect(status().isCreated()));
        assertThat(r.get("bodyRegion").asText()).isEqualTo("LF_HOOF");
        JsonNode seenByVet = body(as(vet, get("/api/incidents/" + r.get("id").asLong())).andExpect(status().isOk()));
        assertThat(seenByVet.get("bodyRegion").asText()).isEqualTo("LF_HOOF");
        assertThat(catalogName("LF_HOOF")).isEqualTo("Móng trước trái");

        JsonNode none = body(sendIncident(groom, horse, "Ngựa bỏ ăn", null).andExpect(status().isCreated()));
        assertThat(none.get("bodyRegion").isNull()).isTrue();
        sendIncident(groom, horse, "Sưng", "LEFT_WING")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.bodyRegion").exists());
        // The Groom reads the catalog to choose a region (TECH-053).
        as(groom, get("/api/body-regions")).andExpect(status().isOk());
    }

    @Test
    void AC_173_examinationReportAndMarkUseTheSameRegion() throws Exception {
        long horse = eligibleHorse();
        long exam = body(recordExam(horse, Map.of("bodyRegion", "LF_FETLOCK")).andExpect(status().isCreated()))
                .get("id").asLong();
        long report = body(sendIncident(account(Role.GROOM), horse, "Sưng khớp", "LF_FETLOCK")
                .andExpect(status().isCreated())).get("id").asLong();
        JsonNode mark = body(asJson(vet, post("/api/horses/" + horse + "/injury-marks"),
                Map.of("regionCode", "LF_FETLOCK", "examinationId", exam)).andExpect(status().isCreated()));

        assertThat(exams(vet, horse).get(0).get("bodyRegion").asText()).isEqualTo("LF_FETLOCK");
        assertThat(body(as(vet, get("/api/incidents/" + report))).get("bodyRegion").asText()).isEqualTo("LF_FETLOCK");
        assertThat(mark.get("regionCode").asText()).isEqualTo("LF_FETLOCK");
        // One name per code, served from the one catalog.
        JsonNode catalog = body(as(vet, get("/api/body-regions")));
        assertThat(StreamSupport.stream(catalog.spliterator(), false).map(r -> r.get("vi").asText()).distinct().count())
                .isEqualTo(catalog.size());
        assertThat(StreamSupport.stream(catalog.spliterator(), false).map(r -> r.get("vi").asText()))
                .noneMatch(n -> n.contains("sau sau"));
    }

    @Test
    void catalogRefusedForOwnerAndClubManager() throws Exception {
        as(owner, get("/api/body-regions")).andExpect(status().isForbidden());
        as(manager, get("/api/body-regions")).andExpect(status().isForbidden());
        as(trainer, get("/api/body-regions")).andExpect(status().isOk());
    }
}
