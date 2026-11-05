package vn.racehorse.video;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MultipartFile;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;
import vn.racehorse.video.entity.TrialVideo;
import vn.racehorse.video.service.TrialVideoService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trial-run videos (REQ-027): AC-147 to AC-167. */
public class TrialVideosTest extends TrainingTestBase {

    /** The smallest file the MP4 check accepts: an ISO media "ftyp" box, then some bytes. */
    static final byte[] MP4 = mp4(64);

    @Autowired
    TrialVideoService service;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    public static byte[] mp4(int size) {
        byte[] b = new byte[size];
        byte[] head = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0, 'i', 's', 'o', 'm', 'm', 'p', '4', '1'};
        System.arraycopy(head, 0, b, 0, head.length);
        return b;
    }

    // ---- fixtures -------------------------------------------------------------------------------------------

    long completedTrialRun(long horse) throws Exception {
        long plan = activePlan(horse);
        long session = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), sessionBody(today(), "TRIAL_RUN", false))
                .andExpect(status().isCreated())).get("id").asLong();
        start(session, false);
        end(session);
        return session;
    }

    ResultActions upload(AccountPrincipal who, long session, byte[] content, String name) throws Exception {
        return as(who, multipart("/api/sessions/" + session + "/videos")
                .file(new MockMultipartFile("file", name, "video/mp4", content)));
    }

    long uploaded(long session) throws Exception {
        return body(upload(trainer, session, MP4, "chay-thu.mp4").andExpect(status().isCreated())).get("id").asLong();
    }

    JsonNode list(AccountPrincipal who, long session) throws Exception {
        return body(as(who, get("/api/sessions/" + session + "/videos")).andExpect(status().isOk()));
    }

    JsonNode auditEntries(String action, long session) throws Exception {
        AccountPrincipal reader = account(Role.CLUB_MANAGER);
        JsonNode all = body(as(reader, get("/api/audit").param("actorId", trainer.id().toString()))
                .andExpect(status().isOk()));
        return json.valueToTree(StreamSupport.stream(all.spliterator(), false)
                .filter(e -> e.get("action").asText().equals(action) && e.get("targetId").asLong() == session)
                .toList());
    }

    void signIn(AccountPrincipal who) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(who, null, who.getAuthorities()));
    }

    /** A file of the given reported size whose content is generated while it is read, never held in memory. */
    static MultipartFile streamed(long size) {
        return new MultipartFile() {
            public String getName() { return "file"; }
            public String getOriginalFilename() { return "lon.mp4"; }
            public String getContentType() { return "video/mp4"; }
            public boolean isEmpty() { return size == 0; }
            public long getSize() { return size; }
            public byte[] getBytes() { throw new UnsupportedOperationException(); }
            public InputStream getInputStream() {
                byte[] head = mp4(24);
                InputStream zeros = new InputStream() {
                    long left = size - head.length;
                    public int read() { return left-- > 0 ? 0 : -1; }
                    public int read(byte[] b, int off, int len) {
                        if (left <= 0) return -1;
                        int n = (int) Math.min(len, left);
                        java.util.Arrays.fill(b, off, off + n, (byte) 0);
                        left -= n;
                        return n;
                    }
                };
                return new SequenceInputStream(new ByteArrayInputStream(head), zeros);
            }
            public void transferTo(File dest) { throw new UnsupportedOperationException(); }
            public void transferTo(Path dest) { throw new UnsupportedOperationException(); }
        };
    }

    // ---- US-027 upload --------------------------------------------------------------------------------------

    @Test
    void AC_147_mp4Of150MbSavedAndListedWithSizeAndTime() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        long size = 150L * 1024 * 1024;
        signIn(trainer);
        TrialVideo saved = service.upload(session, streamed(size), null);
        SecurityContextHolder.clearContext();

        JsonNode videos = list(trainer, session);
        assertThat(videos).hasSize(1);
        assertThat(videos.get(0).get("id").asLong()).isEqualTo(saved.getId());
        assertThat(videos.get(0).get("sizeBytes").asLong()).isEqualTo(size);
        assertThat(videos.get(0).get("fileName").asText()).isEqualTo("lon.mp4");
        assertThat(videos.get(0).get("uploadedAt").asText()).isNotBlank();
        as(trainer, delete("/api/videos/" + saved.getId())).andExpect(status().isNoContent());
    }

    @Test
    void AC_148_autoClosedTrialRunAcceptsVideo() throws Exception {
        long plan = activePlan(eligibleHorse());
        long session = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), sessionBody(today(), "TRIAL_RUN", false))
                .andExpect(status().isCreated())).get("id").asLong();
        start(session, false);
        TestClock.shift(Duration.ofMinutes(125));
        sessionService.autoClose();
        TestClock.reset();
        assertThat(state(session)).isEqualTo("AUTO_CLOSED");

        upload(trainer, session, MP4, "a.mp4").andExpect(status().isCreated());
        assertThat(list(trainer, session)).hasSize(1);
    }

    @Test
    void AC_149_thirdVideoSaved() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        uploaded(session);
        uploaded(session);
        upload(trainer, session, MP4, "ba.mp4").andExpect(status().isCreated());
        assertThat(list(trainer, session)).hasSize(3);
    }

    @Test
    void AC_150_fourthVideoRefusedLimitNamed() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        uploaded(session);
        uploaded(session);
        uploaded(session);
        upload(trainer, session, MP4, "bon.mp4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value("Mỗi buổi tập có tối đa 3 video."));
        assertThat(list(trainer, session)).hasSize(3);
    }

    @Test
    void AC_151_notMp4RefusedFormatNamed() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        upload(trainer, session, "not a video at all".getBytes(), "clip.mov")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_MP4"))
                .andExpect(jsonPath("$.message").value("Chỉ nhận video định dạng MP4."));
        // A renamed file is judged by its content, not its name.
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
        upload(trainer, session, jpeg, "anh.mp4")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_MP4"));
        assertThat(list(trainer, session)).isEmpty();
    }

    @Test
    void AC_152_over200MbRefusedLimitNamed() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        signIn(trainer);
        assertThatThrownBy(() -> service.upload(session, streamed(TrialVideoService.MAX_BYTES + 1), null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo("VIDEO_TOO_LARGE");
                    assertThat(e.getMessage()).contains("200 MB");
                });
        SecurityContextHolder.clearContext();
        assertThat(list(trainer, session)).isEmpty();
    }

    @Test
    void AC_153_sessionInProgressOrNotTrialRunRefusedWithReason() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long trial = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), sessionBody(today(), "TRIAL_RUN", false))
                .andExpect(status().isCreated())).get("id").asLong();
        start(trial, false);
        upload(trainer, trial, MP4, "a.mp4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_COMPLETED"))
                .andExpect(jsonPath("$.message").value("Chỉ thêm video khi buổi chạy thử đã Hoàn thành."));
        end(trial);

        long gallop = schedule(plan, today());
        start(gallop, false);
        end(gallop);
        upload(trainer, gallop, MP4, "a.mp4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_TRIAL_RUN"))
                .andExpect(jsonPath("$.message").value("Chỉ buổi Chạy thử mới có video."));
    }

    @Test
    void AC_154_webLinkInsteadOfFileRefused() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        as(trainer, post("/api/sessions/" + session + "/videos").param("url", "https://example.com/video.mp4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VIDEO_FILE_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Chỉ nhận tệp video tải lên, không nhận đường dẫn."));
        // A link sent alongside a file is refused too.
        as(trainer, multipart("/api/sessions/" + session + "/videos")
                .file(new MockMultipartFile("file", "a.mp4", "video/mp4", MP4))
                .param("url", "https://example.com/video.mp4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VIDEO_FILE_REQUIRED"));
        assertThat(list(trainer, session)).isEmpty();
    }

    @Test
    void AC_155_archivedHorseRefusedOwnerStillWatches() throws Exception {
        long horse = eligibleHorse();
        long session = completedTrialRun(horse);
        long video = uploaded(session);
        asJson(manager, post("/api/horses/" + horse + "/archive"), Map.of("reason", "RETIRED")).andExpect(status().isOk());

        upload(trainer, session, MP4, "moi.mp4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
        assertThat(list(owner, session)).hasSize(1);
        as(owner, get("/api/videos/" + video + "/content")).andExpect(status().isOk());
    }

    @Test
    void AC_156_deletedVideoNotListedNotPlayableAndAudited() throws Exception {
        long horse = eligibleHorse();
        long session = completedTrialRun(horse);
        long video = uploaded(session);

        as(trainer, delete("/api/videos/" + video)).andExpect(status().isNoContent());

        assertThat(list(trainer, session)).isEmpty();
        as(trainer, get("/api/videos/" + video + "/content")).andExpect(status().isNotFound());
        JsonNode entries = auditEntries("TRIAL_VIDEO_DELETED", session);
        assertThat(entries).hasSize(1);
        JsonNode e = entries.get(0);
        assertThat(e.get("actorId").asLong()).isEqualTo(trainer.id());
        assertThat(e.get("occurredAt").asText()).isNotBlank();
        assertThat(e.get("targetType").asText()).isEqualTo("SESSION");
        assertThat(e.get("details").asText()).contains("\"horseId\":" + horse);
    }

    @Test
    void AC_157_uploadAuditedWithWhoWhenSessionAndHorse() throws Exception {
        long horse = eligibleHorse();
        long session = completedTrialRun(horse);
        uploaded(session);

        JsonNode entries = auditEntries("TRIAL_VIDEO_UPLOADED", session);
        assertThat(entries).hasSize(1);
        JsonNode e = entries.get(0);
        assertThat(e.get("actorName").asText()).isEqualTo(trainer.displayName());
        assertThat(e.get("occurredAt").asText()).isNotBlank();
        assertThat(e.get("targetId").asLong()).isEqualTo(session);
        assertThat(e.get("details").asText()).contains("\"horseId\":" + horse).contains(horseName(horse));
    }

    @Test
    void AC_158_veterinarianCannotUploadOrDelete() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        long video = uploaded(session);
        upload(vet, session, MP4, "a.mp4").andExpect(status().isForbidden());
        as(vet, delete("/api/videos/" + video)).andExpect(status().isForbidden());
        assertThat(list(trainer, session)).hasSize(1);
    }

    @Test
    void AC_159_ownerCannotDelete() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        long video = uploaded(session);
        as(owner, delete("/api/videos/" + video)).andExpect(status().isForbidden());
        upload(owner, session, MP4, "a.mp4").andExpect(status().isForbidden());
        assertThat(list(owner, session)).hasSize(1);
    }

    // ---- US-028 watch ---------------------------------------------------------------------------------------

    @Test
    void AC_160_ownerPlaysBothVideosWithSeeking() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        long first = uploaded(session);
        long second = uploaded(session);

        JsonNode videos = list(owner, session);
        assertThat(videos).hasSize(2);
        for (long id : new long[] {first, second}) {
            byte[] played = as(owner, get("/api/videos/" + id + "/content"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Content-Type", "video/mp4"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(played).isEqualTo(MP4);
        }
        byte[] part = as(owner, get("/api/videos/" + first + "/content").header("Range", "bytes=4-7"))
                .andExpect(status().isPartialContent())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(part)).isEqualTo("ftyp");
    }

    @Test
    void AC_161_headTrainerPlaysVideosOfDifferentOwners() throws Exception {
        long sessionA = completedTrialRun(eligibleHorse());
        AccountPrincipal firstOwner = owner;
        owner = account(Role.HORSE_OWNER);
        long sessionB = completedTrialRun(eligibleHorse());
        long a = uploaded(sessionA);
        long b = uploaded(sessionB);

        as(trainer, get("/api/videos/" + a + "/content")).andExpect(status().isOk());
        as(trainer, get("/api/videos/" + b + "/content")).andExpect(status().isOk());
        as(firstOwner, get("/api/videos/" + b + "/content")).andExpect(status().isForbidden());
    }

    @Test
    void AC_162_ownerOfAnotherHorseRefused() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        long video = uploaded(session);
        AccountPrincipal other = account(Role.HORSE_OWNER);
        as(other, get("/api/sessions/" + session + "/videos")).andExpect(status().isForbidden());
        as(other, get("/api/videos/" + video + "/content")).andExpect(status().isForbidden());
    }

    @Test
    void AC_163_clubManagerRefused() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        long video = uploaded(session);
        as(manager, get("/api/sessions/" + session + "/videos")).andExpect(status().isForbidden());
        as(manager, get("/api/videos/" + video + "/content")).andExpect(status().isForbidden());
    }

    @Test
    void AC_164_veterinarianRefused() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        long video = uploaded(session);
        as(vet, get("/api/sessions/" + session + "/videos")).andExpect(status().isForbidden());
        as(vet, get("/api/videos/" + video + "/content")).andExpect(status().isForbidden());
    }

    @Test
    void AC_165_ownerChangeMovesAccess() throws Exception {
        long horse = eligibleHorse();
        long session = completedTrialRun(horse);
        long video = uploaded(session);
        AccountPrincipal previous = owner;
        AccountPrincipal next = account(Role.HORSE_OWNER);

        JsonNode h = body(as(manager, get("/api/horses/" + horse)).andExpect(status().isOk()));
        Map<String, Object> update = new HashMap<>(horseBody(h.get("name").asText(), h.get("sex").asText(), next.id()));
        asJson(manager, put("/api/horses/" + horse), update).andExpect(status().isOk());

        assertThat(list(next, session)).hasSize(1);
        as(next, get("/api/videos/" + video + "/content")).andExpect(status().isOk());
        as(previous, get("/api/sessions/" + session + "/videos")).andExpect(status().isForbidden());
        as(previous, get("/api/videos/" + video + "/content")).andExpect(status().isForbidden());
    }

    @Test
    void AC_166_completedTrialRunWithoutVideoListsNone() throws Exception {
        long horse = eligibleHorse();
        long session = completedTrialRun(horse);
        assertThat(list(owner, session)).isEmpty();
        JsonNode schedule = body(as(owner, get("/api/horses/" + horse + "/schedule")).andExpect(status().isOk()));
        JsonNode item = StreamSupport.stream(schedule.spliterator(), false)
                .filter(s -> s.get("id").asLong() == session).findFirst().orElseThrow();
        assertThat(item.get("videoCount").asLong()).isZero();
        uploaded(session);
        schedule = body(as(owner, get("/api/horses/" + horse + "/schedule")));
        item = StreamSupport.stream(schedule.spliterator(), false)
                .filter(s -> s.get("id").asLong() == session).findFirst().orElseThrow();
        assertThat(item.get("videoCount").asLong()).isEqualTo(1);
    }

    @Test
    void AC_167_deletedVideoLinkNotFound() throws Exception {
        long session = completedTrialRun(eligibleHorse());
        long video = uploaded(session);
        as(trainer, delete("/api/videos/" + video)).andExpect(status().isNoContent());
        as(owner, get("/api/videos/" + video + "/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
