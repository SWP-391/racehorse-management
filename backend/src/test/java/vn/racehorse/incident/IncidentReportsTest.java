package vn.racehorse.incident;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Groom's incident reports (BR-070, BR-071). */
class IncidentReportsTest extends TrainingTestBase {

    private AccountPrincipal groom;

    private static byte[] jpeg(int size) {
        byte[] b = new byte[size];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return b;
    }

    private static byte[] png() {
        byte[] b = new byte[64];
        byte[] magic = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(magic, 0, b, 0, magic.length);
        return b;
    }

    private ResultActions send(AccountPrincipal who, Long horseId, String description, MockMultipartFile... files) throws Exception {
        var request = multipart("/api/incidents");
        for (MockMultipartFile f : files) {
            request.file(f);
        }
        if (horseId != null) request.param("horseId", horseId.toString());
        if (description != null) request.param("description", description);
        return mvc.perform(request.with(user(who)).with(csrf()));
    }

    private static MockMultipartFile photo(String name, byte[] bytes) {
        return new MockMultipartFile("photos", name, "image/jpeg", bytes);
    }

    private AccountPrincipal groom() {
        if (groom == null) groom = account(Role.GROOM);
        return groom;
    }

    @Test
    void AC_102_reportWithAPhotoIsNewVeterinarianNotifiedStatusUnchanged() throws Exception {
        long horse = eligibleHorse();
        JsonNode r = body(send(groom(), horse, "Ngựa bỏ ăn từ sáng", photo("bua-an.jpg", jpeg(2048))).andExpect(status().isCreated()));
        assertThat(r.get("state").asText()).isEqualTo("NEW");
        assertThat(r.get("photoIds").size()).isEqualTo(1);
        assertThat(hasNotification(vet, "Báo cáo sự cố mới về ngựa \"" + horseName(horse) + "\"")).isTrue();
        assertThat(health(horse).get("status").asText()).isEqualTo("ELIGIBLE");
        assertThat(health(horse).get("lock").isNull()).isTrue();
    }

    @Test
    void AC_103_openedThenClosedWithANoteLinkedToAnExamination() throws Exception {
        long horse = eligibleHorse();
        long id = body(send(groom(), horse, "Có dấu hiệu đau bụng").andExpect(status().isCreated())).get("id").asLong();
        assertThat(body(as(groom(), get("/api/incidents/" + id))).get("state").asText()).isEqualTo("NEW");

        assertThat(body(as(vet, get("/api/incidents/" + id)).andExpect(status().isOk())).get("state").asText())
                .isEqualTo("ACKNOWLEDGED");
        long exam = body(asJson(vet, post("/api/horses/" + horse + "/examinations"),
                Map.of("diagnosis", "Đau bụng nhẹ", "weightKg", 470)).andExpect(status().isCreated())).get("id").asLong();
        JsonNode closed = body(asJson(vet, post("/api/incidents/" + id + "/close"),
                Map.of("note", "Đã khám, theo dõi 24 giờ", "examinationId", exam)).andExpect(status().isOk()));
        assertThat(closed.get("state").asText()).isEqualTo("HANDLED");
        assertThat(closed.get("linkedExaminationId").asLong()).isEqualTo(exam);

        JsonNode mine = body(as(groom(), get("/api/incidents")));
        assertThat(mine.get(0).get("state").asText()).isEqualTo("HANDLED");
        assertThat(mine.get(0).get("closingNote").asText()).isEqualTo("Đã khám, theo dõi 24 giờ");
    }

    @Test
    void anExaminationOfAnotherHorseCannotBeLinked() throws Exception {
        long horse = eligibleHorse();
        long other = eligibleHorse();
        long id = body(send(groom(), horse, "Móng bị xước").andExpect(status().isCreated())).get("id").asLong();
        long exam = body(asJson(vet, post("/api/horses/" + other + "/examinations"), Map.of("diagnosis", "x"))
                .andExpect(status().isCreated())).get("id").asLong();
        asJson(vet, post("/api/incidents/" + id + "/close"), Map.of("note", "x", "examinationId", exam))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXAMINATION"));
    }

    @Test
    void AC_104_reportWithoutAPhotoAccepted() throws Exception {
        long horse = eligibleHorse();
        send(groom(), horse, "Ngựa sốt nhẹ").andExpect(status().isCreated()).andExpect(jsonPath("$.photoIds.length()").value(0));
    }

    @Test
    void AC_105_reportWithoutADescriptionRefused() throws Exception {
        long horse = eligibleHorse();
        send(groom(), horse, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.description").exists());
        send(groom(), horse, null).andExpect(status().isBadRequest());
    }

    @Test
    void AC_120_photoLimitsFourthOverTenMegabytesAndWrongTypeRefusedThreeValidAccepted() throws Exception {
        long horse = eligibleHorse();
        send(groom(), horse, "x", photo("1.jpg", jpeg(100)), photo("2.jpg", jpeg(100)), photo("3.jpg", png()),
                photo("4.jpg", jpeg(100)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.photos").value(org.hamcrest.Matchers.containsString("Tối đa 3")));
        send(groom(), horse, "x", photo("lon.jpg", jpeg(10 * 1024 * 1024 + 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['photos[0]']").value(org.hamcrest.Matchers.containsString("10 MB")));
        send(groom(), horse, "x", new MockMultipartFile("photos", "giả.jpg", "image/jpeg", "<html>not an image</html>".getBytes()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['photos[0]']").value(org.hamcrest.Matchers.containsString("JPG hoặc PNG")));
        send(groom(), horse, "x", photo("1.jpg", jpeg(100)), new MockMultipartFile("photos", "2.png", "image/png", png()),
                photo("3.jpg", jpeg(10 * 1024 * 1024)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.photoIds.length()").value(3));
    }

    @Test
    void photosAreServedOnlyToTheSenderAndVeterinarians() throws Exception {
        long horse = eligibleHorse();
        JsonNode r = body(send(groom(), horse, "x", photo("a.jpg", jpeg(500))).andExpect(status().isCreated()));
        long id = r.get("id").asLong();
        long photo = r.get("photoIds").get(0).asLong();
        as(groom(), get("/api/incidents/" + id + "/photos/" + photo)).andExpect(status().isOk());
        as(vet, get("/api/incidents/" + id + "/photos/" + photo)).andExpect(status().isOk());
        as(account(Role.GROOM), get("/api/incidents/" + id + "/photos/" + photo)).andExpect(status().isForbidden());
        as(trainer, get("/api/incidents/" + id + "/photos/" + photo)).andExpect(status().isForbidden());
        as(owner, get("/api/incidents")).andExpect(status().isForbidden());
    }

    @Test
    void AC_106_groomSeesOnlyNameAndRegistrationNumber() throws Exception {
        long horse = eligibleHorse();
        as(groom(), get("/api/horses")).andExpect(status().isForbidden());
        as(groom(), get("/api/horses/" + horse)).andExpect(status().isForbidden());
        as(groom(), get("/api/horses/" + horse + "/health")).andExpect(status().isForbidden());
        as(groom(), get("/api/horses/" + horse + "/schedule")).andExpect(status().isForbidden());
        as(groom(), get("/api/horses/" + horse + "/readiness")).andExpect(status().isForbidden());

        JsonNode options = body(as(groom(), get("/api/horse-options")).andExpect(status().isOk()));
        JsonNode mine = null;
        for (JsonNode o : options) {
            if (o.get("id").asLong() == horse) mine = o;
        }
        assertThat(mine).isNotNull();
        Map<String, Boolean> keys = new HashMap<>();
        mine.fieldNames().forEachRemaining(k -> keys.put(k, true));
        assertThat(keys.keySet()).containsExactlyInAnyOrder("id", "name", "registrationNumber");
        as(owner, get("/api/horse-options")).andExpect(status().isForbidden());
    }

    @Test
    void aGroomCannotReadAnotherGroomsReport() throws Exception {
        long horse = eligibleHorse();
        long id = body(send(groom(), horse, "x").andExpect(status().isCreated())).get("id").asLong();
        as(account(Role.GROOM), get("/api/incidents/" + id)).andExpect(status().isForbidden());
        asJson(groom(), post("/api/incidents/" + id + "/close"), Map.of("note", "x")).andExpect(status().isForbidden());
    }
}
