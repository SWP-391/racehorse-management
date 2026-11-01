package vn.racehorse.video.service;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.training.service.TrainingEnums.SessionState;
import vn.racehorse.training.service.TrainingEnums.SessionType;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.repository.TrainingSessionRepository;
import vn.racehorse.video.dto.TrialVideoContent;
import vn.racehorse.video.entity.TrialVideo;
import vn.racehorse.video.repository.TrialVideoRepository;
import vn.racehorse.video.storage.VideoStorage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Trial-run videos (REQ-027): the Head Trainer uploads and deletes, the Head Trainer and the horse's current Owner
 * watch (BR-104 to BR-109). Files go to {@link VideoStorage}; only metadata is in the database (TECH-005).
 */
@Service
public class TrialVideoService {

    /** 200 MB (BR-105). */
    public static final long MAX_BYTES = 200L * 1024 * 1024;
    public static final int MAX_PER_SESSION = 3;
    public static final String TOO_LARGE = "Video vượt quá giới hạn 200 MB.";

    private static final Set<SessionState> COMPLETED = EnumSet.of(SessionState.COMPLETED, SessionState.AUTO_CLOSED);
    private static final byte[] FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);

    private final TrialVideoRepository videos;
    private final TrainingSessionRepository sessions;
    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final VideoStorage storage;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final TransactionTemplate tx;

    public TrialVideoService(TrialVideoRepository videos, TrainingSessionRepository sessions, HorseRepository horses,
                             UserAccountRepository accounts, VideoStorage storage, AccessGuard access,
                             AuditService audit, ClubClock clock, TransactionTemplate tx) {
        this.videos = videos;
        this.sessions = sessions;
        this.horses = horses;
        this.accounts = accounts;
        this.storage = storage;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.tx = tx;
    }

    /**
     * Checks every rule first, writes the file outside any transaction, then records it; the file is removed if
     * recording fails (TECH-053).
     */
    public TrialVideo upload(Long sessionId, MultipartFile file, String url) {
        AccountPrincipal actor = access.require(Permission.MANAGE_TRIAL_VIDEOS);
        if ((url != null && !url.isBlank()) || file == null || file.isEmpty()) {
            throw ApiException.invalid("VIDEO_FILE_REQUIRED", "file",
                    "Chỉ nhận tệp video tải lên, không nhận đường dẫn.");
        }
        TrainingSession session = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("buổi tập"));
        access.requireTrainerOf(Permission.MANAGE_TRIAL_VIDEOS, session.getHorseId());
        Horse horse = horses.findById(session.getHorseId()).orElseThrow(() -> ApiException.notFound("ngựa"));
        requireAccepting(session, horse);
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.invalid("VIDEO_TOO_LARGE", "file", TOO_LARGE);
        }
        VideoStorage.Stored stored;
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            if (!isMp4(in)) {
                throw ApiException.invalid("VIDEO_NOT_MP4", "file", "Chỉ nhận video định dạng MP4.");
            }
            stored = storage.store(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (stored.size() > MAX_BYTES) {
            storage.delete(stored.key());
            throw ApiException.invalid("VIDEO_TOO_LARGE", "file", TOO_LARGE);
        }
        try {
            return tx.execute(status -> {
                // Re-checked in the transaction: the session may have changed while the file was being written.
                TrainingSession current = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("buổi tập"));
                requireAccepting(current, horses.findById(current.getHorseId()).orElseThrow());
                var by = accounts.findById(actor.id()).orElseThrow();
                TrialVideo video = videos.save(new TrialVideo(sessionId, horse.getId(), fileName(file),
                        stored.size(), stored.key(), by, clock.now()));
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("videoId", video.getId());
                details.put("horseId", horse.getId());
                details.put("horseName", horse.getName());
                details.put("fileName", video.getFileName());
                details.put("sizeBytes", video.getSizeBytes());
                audit.record(actor, "TRIAL_VIDEO_UPLOADED", "SESSION", sessionId, details);
                return video;
            });
        } catch (RuntimeException e) {
            storage.delete(stored.key());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<TrialVideo> list(Long sessionId) {
        TrainingSession session = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("buổi tập"));
        requireWatch(session.getHorseId());
        return videos.findBySessionIdOrderByIdAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public TrialVideoContent content(Long videoId) {
        AccountPrincipal user = access.require(Permission.WATCH_TRIAL_VIDEOS);
        TrialVideo video = videos.findById(videoId).orElseThrow(() -> ApiException.notFound("video"));
        requireOwnerScope(user, video.getHorseId());
        Resource resource = storage.open(video.getStorageKey());
        if (!resource.exists()) {
            throw ApiException.notFound("video");
        }
        return new TrialVideoContent(video, resource);
    }

    @Transactional
    public void delete(Long videoId) {
        AccountPrincipal actor = access.require(Permission.MANAGE_TRIAL_VIDEOS);
        TrialVideo video = videos.findById(videoId).orElseThrow(() -> ApiException.notFound("video"));
        access.requireTrainerOf(Permission.MANAGE_TRIAL_VIDEOS, video.getHorseId());
        Horse horse = horses.findById(video.getHorseId()).orElseThrow();
        videos.delete(video);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("videoId", videoId);
        details.put("horseId", horse.getId());
        details.put("horseName", horse.getName());
        details.put("fileName", video.getFileName());
        audit.record(actor, "TRIAL_VIDEO_DELETED", "SESSION", video.getSessionId(), details);
        String key = video.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }

    /** Number of videos per session, for the Owner's schedule (TECH-056). */
    @Transactional(readOnly = true)
    public Map<Long, Long> counts(Collection<Long> sessionIds) {
        Map<Long, Long> result = new HashMap<>();
        if (sessionIds.isEmpty()) return result;
        for (Object[] row : videos.countBySessionIds(sessionIds)) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }

    private void requireAccepting(TrainingSession session, Horse horse) {
        if (session.getType() != SessionType.TRIAL_RUN) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_TRIAL_RUN",
                    "Chỉ buổi Chạy thử mới có video.");
        }
        if (!COMPLETED.contains(session.getState())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_COMPLETED",
                    "Chỉ thêm video khi buổi chạy thử đã Hoàn thành.");
        }
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: hồ sơ chỉ xem.");
        }
        if (videos.countBySessionId(session.getId()) >= MAX_PER_SESSION) {
            throw new ApiException(HttpStatus.CONFLICT, "VIDEO_LIMIT_REACHED",
                    "Mỗi buổi tập có tối đa 3 video.", Map.of("file", "Mỗi buổi tập có tối đa 3 video."));
        }
    }

    private void requireWatch(Long horseId) {
        AccountPrincipal user = access.require(Permission.WATCH_TRIAL_VIDEOS);
        requireOwnerScope(user, horseId);
    }

    /** An Owner watches only the horses they own now (BR-106, BR-006). */
    private void requireOwnerScope(AccountPrincipal user, Long horseId) {
        if (user.role() != Role.HORSE_OWNER) return;
        Horse horse = horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (!Objects.equals(horse.getOwner().getId(), user.id())) {
            throw ApiException.forbidden();
        }
    }

    /** ISO base media files (MP4) carry the box type "ftyp" at bytes 4 to 7 (TECH-054). */
    static boolean isMp4(InputStream in) throws IOException {
        in.mark(16);
        byte[] head = in.readNBytes(8);
        in.reset();
        return head.length == 8 && Arrays.equals(Arrays.copyOfRange(head, 4, 8), FTYP);
    }

    private static String fileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) return "video.mp4";
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) return "video.mp4";
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
