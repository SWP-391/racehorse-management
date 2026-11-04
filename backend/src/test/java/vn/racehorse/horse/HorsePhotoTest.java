package vn.racehorse.horse;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One photo per horse, added or replaced by the Club Manager and read with the profile's visibility (BR-184, AC-361 to AC-364). */
class HorsePhotoTest extends TrainingTestBase {

    private static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, (x * 3 << 16) | (y * 2 << 8) | 90);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private ResultActions put(AccountPrincipal who, long horse, String name, String type, byte[] bytes) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/horses/" + horse + "/photo")
                .file(new MockMultipartFile("file", name, type, bytes)).with(user(who)).with(csrf()));
    }

    private int auditCount(AccountPrincipal actor, String action) throws Exception {
        JsonNode all = body(as(manager, get("/api/audit").param("actorId", actor.id().toString())));
        return (int) StreamSupport.stream(all.spliterator(), false).filter(e -> action.equals(e.get("action").asText())).count();
    }

    @Test
    void AC_361_aJpgIsSavedAudited_andServedInTwoSizes() throws Exception {
        long horse = newHorse();
        byte[] jpg = image("jpg", 2000, 1500);
        ResultActions saved = put(manager, horse, "ngua.jpg", "image/jpeg", jpg).andExpect(status().isOk());
        assertThat(body(saved).get("photoVersion").asLong()).isPositive();
        byte[] full = as(manager, get("/api/horses/" + horse + "/photo")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] thumb = as(manager, get("/api/horses/" + horse + "/photo").param("variant", "thumb")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage f = ImageIO.read(new ByteArrayInputStream(full));
        BufferedImage t = ImageIO.read(new ByteArrayInputStream(thumb));
        assertThat(Math.max(f.getWidth(), f.getHeight())).isEqualTo(1280);
        assertThat(Math.max(t.getWidth(), t.getHeight())).isEqualTo(320);
        assertThat(auditCount(manager, "HORSE_PHOTO_ADDED")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aPngStaysAPng() throws Exception {
        long horse = newHorse();
        put(manager, horse, "ngua.png", "image/png", image("png", 400, 300)).andExpect(status().isOk());
        as(manager, get("/api/horses/" + horse + "/photo")).andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void AC_362_aRenamedDocumentALargeFileAndATruncatedImageAreRefused_andTheEarlierPhotoStays() throws Exception {
        long horse = newHorse();
        put(manager, horse, "ngua.jpg", "image/jpeg", image("jpg", 300, 200)).andExpect(status().isOk());
        long version = body(as(manager, get("/api/horses/" + horse))).get("photoVersion").asLong();

        put(manager, horse, "ghi-chu.jpg", "image/jpeg", "đây không phải là ảnh".getBytes()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_TYPE"));
        put(manager, horse, "lon.jpg", "image/jpeg", new byte[6 * 1024 * 1024]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
        byte[] png = image("png", 200, 200);
        byte[] truncated = java.util.Arrays.copyOf(png, png.length / 3);
        put(manager, horse, "cut.png", "image/png", truncated).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_TYPE"));

        assertThat(body(as(manager, get("/api/horses/" + horse))).get("photoVersion").asLong()).isEqualTo(version);
        as(manager, get("/api/horses/" + horse + "/photo")).andExpect(status().isOk());
    }

    @Test
    void AC_363_aReplacementIsAuditedAndChangesTheVersion() throws Exception {
        long horse = newHorse();
        put(manager, horse, "a.jpg", "image/jpeg", image("jpg", 300, 200)).andExpect(status().isOk());
        long first = body(as(manager, get("/api/horses/" + horse))).get("photoVersion").asLong();
        Thread.sleep(5);
        put(manager, horse, "b.jpg", "image/jpeg", image("jpg", 320, 240)).andExpect(status().isOk());
        long second = body(as(manager, get("/api/horses/" + horse))).get("photoVersion").asLong();
        assertThat(second).isGreaterThan(first);
        assertThat(auditCount(manager, "HORSE_PHOTO_REPLACED")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void AC_364_onlyTheClubManagerUploads_andOnlyTheRightOwnerReads() throws Exception {
        long horse = newHorse();
        put(manager, horse, "a.jpg", "image/jpeg", image("jpg", 300, 200)).andExpect(status().isOk());
        for (AccountPrincipal who : new AccountPrincipal[] {trainer, vet, owner, account(Role.GROOM)}) {
            put(who, horse, "x.jpg", "image/jpeg", image("jpg", 300, 200)).andExpect(status().isForbidden());
        }
        as(owner, get("/api/horses/" + horse + "/photo")).andExpect(status().isOk());
        as(account(Role.HORSE_OWNER), get("/api/horses/" + horse + "/photo")).andExpect(status().isForbidden());
        as(account(Role.GROOM), get("/api/horses/" + horse + "/photo")).andExpect(status().isForbidden());
    }

    @Test
    void aHorseWithoutAPhotoAnswersNotFound_notAnEmptyImage() throws Exception {
        long horse = newHorse();
        as(manager, get("/api/horses/" + horse + "/photo")).andExpect(status().isNotFound());
        assertThat(body(as(manager, get("/api/horses/" + horse))).get("photoVersion").isNull()).isTrue();
    }

    @Test
    void anArchivedHorseCannotGetANewPhotoButItsOwnerStillReadsTheOldOne() throws Exception {
        long horse = newHorse();
        put(manager, horse, "a.jpg", "image/jpeg", image("jpg", 300, 200)).andExpect(status().isOk());
        asJson(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/horses/" + horse + "/archive"),
                java.util.Map.of("reason", "SOLD")).andExpect(status().isOk());
        put(manager, horse, "b.jpg", "image/jpeg", image("jpg", 300, 200)).andExpect(status().isConflict());
        as(owner, get("/api/horses/" + horse + "/photo")).andExpect(status().isOk());
    }

    /** A valid PNG header, a tiny data chunk and the end chunk: a few dozen bytes that declare `w` x `h` pixels. */
    private static byte[] declaredSize(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        java.nio.ByteBuffer ihdr = java.nio.ByteBuffer.allocate(13).putInt(w).putInt(h).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(out, "IHDR", ihdr.array());
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        deflater.setInput(new byte[] {0});
        deflater.finish();
        byte[] data = new byte[64];
        int n = deflater.deflate(data);
        chunk(out, "IDAT", java.util.Arrays.copyOf(data, n));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
        out.write(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());
        byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(t);
        out.write(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(t);
        crc.update(data);
        out.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    @Test
    void aTinyPngThatDeclaresHugeDimensionsIsRefusedBeforeItIsDecoded() throws Exception {
        long horse = newHorse();
        byte[] bomb = declaredSize(30000, 30000);
        assertThat(bomb.length).isLessThan(200);
        put(manager, horse, "bom.png", "image/png", bomb).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_DIMENSIONS"));
        // Within the pixel cap per side but over 40 megapixels in total is refused as well.
        put(manager, horse, "bom2.png", "image/png", declaredSize(7000, 7000)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_DIMENSIONS"));
        as(manager, get("/api/horses/" + horse + "/photo")).andExpect(status().isNotFound());
    }

    @Test
    void photoVersionsListOnlyHorsesWithAPhotoThatTheViewerMaySee() throws Exception {
        long horse = newHorse();
        long without = newHorse();
        put(manager, horse, "ngua.jpg", "image/jpeg", image("jpg", 300, 200)).andExpect(status().isOk());
        JsonNode seen = body(as(manager, get("/api/horses/photo-versions")).andExpect(status().isOk()));
        assertThat(seen.has(String.valueOf(horse))).isTrue();
        assertThat(seen.has(String.valueOf(without))).isFalse();
        assertThat(body(as(owner, get("/api/horses/photo-versions"))).has(String.valueOf(horse))).isTrue();
        assertThat(body(as(account(Role.HORSE_OWNER), get("/api/horses/photo-versions"))).has(String.valueOf(horse))).isFalse();
        as(account(Role.GROOM), get("/api/horses/photo-versions")).andExpect(status().isForbidden());
    }

    @Test
    void aValueOfTheWrongTypeAnswersWithTheStandardErrorNamingTheParameter() throws Exception {
        as(manager, get("/api/horses").param("size", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.fields.size").exists());
        as(manager, get("/api/health").param("status", "BOGUS")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.fields.status").exists());
    }
}
