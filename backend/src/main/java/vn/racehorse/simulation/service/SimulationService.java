package vn.racehorse.simulation.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.events.DomainEvents.SessionEnded;
import vn.racehorse.health.service.ActivityStatusProvider;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.horse.service.HorseTrainerService;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.simulation.dto.SimulationFigures;
import vn.racehorse.simulation.dto.SimulationLive;
import vn.racehorse.simulation.dto.SimulationSummary;
import vn.racehorse.simulation.entity.Measure;
import vn.racehorse.simulation.entity.TelemetryAlert;
import vn.racehorse.simulation.entity.TelemetryThreshold;
import vn.racehorse.simulation.repository.TelemetryAlertRepository;
import vn.racehorse.simulation.repository.TelemetryThresholdRepository;
import vn.racehorse.simulation.service.TelemetrySource.Sample;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.repository.TrainingSessionRepository;
import vn.racehorse.training.service.SessionService;
import vn.racehorse.training.service.TrainingEnums.SessionState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Live monitoring, per-horse alerts and the end-of-session summary, on top of a replaceable {@link TelemetrySource}
 * (REQ-005, BR-050 to BR-053; TECH-002, TECH-003, TECH-039, TECH-040).
 */
@Service
public class SimulationService {

    public static final String LABEL = "DỮ LIỆU MÔ PHỎNG";
    static final int WINDOW_SECONDS = 60;

    private final TelemetrySource source;
    private final TelemetryThresholdRepository thresholds;
    private final TelemetryAlertRepository alerts;
    private final TrainingSessionRepository sessions;
    private final HorseRepository horses;
    private final ActivityStatusProvider activity;
    private final SessionService sessionService;
    private final AccessGuard access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClubClock clock;
    private final HorseTrainerService trainers;

    public SimulationService(TelemetrySource source, TelemetryThresholdRepository thresholds,
                             TelemetryAlertRepository alerts, TrainingSessionRepository sessions,
                             HorseRepository horses, ActivityStatusProvider activity, SessionService sessionService,
                             AccessGuard access, AuditService audit, NotificationService notifications, ClubClock clock,
                             HorseTrainerService trainers) {
        this.source = source;
        this.thresholds = thresholds;
        this.alerts = alerts;
        this.sessions = sessions;
        this.horses = horses;
        this.activity = activity;
        this.sessionService = sessionService;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.trainers = trainers;
    }

    /** What the chart and the figures cover: the last minute, the last five minutes or the whole session (BR-189). */
    public enum Window {
        ONE_MINUTE(60), FIVE_MINUTES(300), SESSION(0);

        final int seconds;

        Window(int seconds) {
            this.seconds = seconds;
        }
    }

    /** Longest series sent for a whole session: a display, so an even stride keeps it light; figures stay exact. */
    static final int MAX_POINTS = 300;

    /** Running totals of one session, advanced second by second so a long session costs one second of samples per call. */
    private static final class Aggregate {
        int last = -1;
        int hrMin = Integer.MAX_VALUE;
        int hrMax = Integer.MIN_VALUE;
        long hrSum;
        double spMin = Double.MAX_VALUE;
        double spMax = -Double.MAX_VALUE;
        double spSum;
        long count;

        void add(Sample s) {
            hrMin = Math.min(hrMin, s.heartRate());
            hrMax = Math.max(hrMax, s.heartRate());
            hrSum += s.heartRate();
            spMin = Math.min(spMin, s.speed());
            spMax = Math.max(spMax, s.speed());
            spSum += s.speed();
            count++;
        }
    }

    private final Map<Long, Aggregate> running = new java.util.concurrent.ConcurrentHashMap<>();

    // ---- live view (BR-050) -------------------------------------------------------------------------------

    /** Samples exist only while the horse is training; an idle horse returns nothing (AC-059). */
    @Transactional
    public SimulationLive live(Long horseId, Window window) {
        access.require(Permission.VIEW_LIVE);
        horse(horseId);
        Long sessionId = activity.sessionInProgress(horseId).orElse(null);
        if (sessionId == null) {
            return new SimulationLive(false, null, null, null, null, null, List.of(), List.of(), List.of(), null);
        }
        TrainingSession s = sessions.findById(sessionId).orElseThrow();
        evaluate(s);
        int now = offset(s.getStartedAt(), clock.now());
        Window w = window == null ? Window.ONE_MINUTE : window;
        List<Sample> recent = new ArrayList<>();
        SimulationSummary summary;
        if (w == Window.SESSION) {
            int stride = Math.max(1, (int) Math.ceil((now + 1) / (double) MAX_POINTS));
            for (int t = now % stride; t < now; t += stride) {
                recent.add(source.sample(sessionId, s.getStartedAt(), t));
            }
            recent.add(source.sample(sessionId, s.getStartedAt(), now));
            summary = sessionSummary(s, sessionId, now, recent.get(recent.size() - 1));
        } else {
            for (int t = Math.max(0, now - w.seconds + 1); t <= now; t++) {
                recent.add(source.sample(sessionId, s.getStartedAt(), t));
            }
            summary = summarize(recent, w.seconds);
        }
        return new SimulationLive(true, sessionId, source.dataSource(), LABEL, now, recent.get(recent.size() - 1), recent,
                alerts.findByHorseIdOrderByRaisedAtDescIdDesc(horseId).stream()
                        .filter(a -> sessionId.equals(a.getSessionId())).toList(),
                thresholds.findByHorseIdOrderByMeasureAsc(horseId), summary);
    }

    private static SimulationSummary summarize(List<Sample> samples, int windowSeconds) {
        Aggregate a = new Aggregate();
        samples.forEach(a::add);
        return figures(a, samples.get(samples.size() - 1), windowSeconds);
    }

    private SimulationSummary sessionSummary(TrainingSession s, Long sessionId, int now, Sample current) {
        Aggregate a = running.computeIfAbsent(sessionId, k -> new Aggregate());
        synchronized (a) {
            for (int t = a.last + 1; t <= now; t++) {
                a.add(source.sample(sessionId, s.getStartedAt(), t));
            }
            a.last = Math.max(a.last, now);
            return figures(a, current, now + 1);
        }
    }

    private static SimulationSummary figures(Aggregate a, Sample current, int windowSeconds) {
        return new SimulationSummary(
                new SimulationFigures(current.heartRate(), a.hrMin, a.hrMax, round1(a.hrSum / (double) a.count)),
                new SimulationFigures(current.speed(), a.spMin, a.spMax, round1(a.spSum / a.count)),
                windowSeconds);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ---- thresholds (BR-052) ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TelemetryThreshold> thresholds(Long horseId) {
        access.require(Permission.SET_THRESHOLDS);
        horse(horseId);
        return thresholds.findByHorseIdOrderByMeasureAsc(horseId);
    }

    /** A null limit clears the threshold. There is never a default. */
    @Transactional
    public List<TelemetryThreshold> setThreshold(Long horseId, Measure measure, BigDecimal limit) {
        AccountPrincipal actor = access.requireTrainerOf(Permission.SET_THRESHOLDS, horseId);
        horse(horseId);
        if (measure == null) {
            throw ApiException.invalid("MEASURE_REQUIRED", "measure", "Bắt buộc chọn chỉ số.");
        }
        var existing = thresholds.findByHorseIdAndMeasure(horseId, measure);
        if (limit == null) {
            existing.ifPresent(thresholds::delete);
            audit.record(actor, "THRESHOLD_CLEARED", "HORSE", horseId, Map.of("measure", measure.name()));
        } else {
            if (limit.signum() <= 0) {
                throw ApiException.invalid("INVALID_LIMIT", "limit", "Ngưỡng phải lớn hơn 0.");
            }
            if (existing.isPresent()) {
                existing.get().change(limit, actor.id(), clock.now());
            } else {
                thresholds.save(new TelemetryThreshold(horseId, measure, limit, actor.id(), clock.now()));
            }
            audit.record(actor, "THRESHOLD_SET", "HORSE", horseId,
                    Map.of("measure", measure.name(), "limit", limit.toPlainString()));
        }
        return thresholds.findByHorseIdOrderByMeasureAsc(horseId);
    }

    @Transactional(readOnly = true)
    public List<TelemetryAlert> alertsOf(Long horseId) {
        access.require(Permission.RECEIVE_ALERTS);
        horse(horseId);
        return alerts.findByHorseIdOrderByRaisedAtDescIdDesc(horseId);
    }

    // ---- alert evaluation (BR-052, TECH-039) --------------------------------------------------------------

    /** Idempotent: scans every session in progress and raises at most one alert per session and measure. */
    @Transactional
    public int evaluate() {
        int raised = 0;
        for (TrainingSession s : sessions.findByState(SessionState.IN_PROGRESS)) {
            raised += evaluate(s);
        }
        return raised;
    }

    private int evaluate(TrainingSession s) {
        Instant now = clock.now();
        int end = offset(s.getStartedAt(), now);
        int raised = 0;
        for (TelemetryThreshold th : thresholds.findByHorseIdOrderByMeasureAsc(s.getHorseId())) {
            if (alerts.existsBySessionIdAndMeasure(s.getId(), th.getMeasure())) {
                continue;
            }
            int from = Math.max(0, offset(s.getStartedAt(), th.getSetAt()));
            for (int t = from; t <= end; t++) {
                Sample sample = source.sample(s.getId(), s.getStartedAt(), t);
                BigDecimal value = th.getMeasure() == Measure.HEART_RATE
                        ? BigDecimal.valueOf(sample.heartRate()) : BigDecimal.valueOf(sample.speed());
                if (value.compareTo(th.getLimit()) > 0) {
                    raise(s, th, value, t, now);
                    raised++;
                    break;
                }
            }
        }
        return raised;
    }

    private void raise(TrainingSession s, TelemetryThreshold th, BigDecimal value, int offsetSecond, Instant now) {
        alerts.save(new TelemetryAlert(s.getId(), s.getHorseId(), th.getMeasure(), th.getLimit(), value, offsetSecond,
                now, source.dataSource()));
        audit.recordSystem("TELEMETRY_ALERT", "SESSION", s.getId(), Map.of("horseId", s.getHorseId(),
                "measure", th.getMeasure().name(), "limit", th.getLimit().toPlainString(), "value", value.toPlainString(),
                "dataSource", source.dataSource().name()));
        String message = "Cảnh báo (" + LABEL + "): ngựa \"" + horseName(s.getHorseId()) + "\" vượt ngưỡng "
                + th.getMeasure().label() + " " + th.getLimit().stripTrailingZeros().toPlainString() + ": "
                + value.stripTrailingZeros().toPlainString() + ".";
        trainers.notifyTrainer(s.getHorseId(), message, "/sessions/" + s.getId());
        notifications.notifyRole(Role.VETERINARIAN, message, "/horses/" + s.getHorseId());
    }

    // ---- summary at session end (BR-051, TECH-040) ---------------------------------------------------------

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onSessionEnded(SessionEnded event) {
        running.remove(event.sessionId());
        TrainingSession s = sessions.findById(event.sessionId()).orElse(null);
        if (s == null || s.getStartedAt() == null || s.getEndedAt() == null) {
            return;
        }
        int end = offset(s.getStartedAt(), s.getEndedAt());
        long sum = 0;
        double maxSpeed = 0;
        for (int t = 0; t <= end; t++) {
            Sample sample = source.sample(s.getId(), s.getStartedAt(), t);
            sum += sample.heartRate();
            maxSpeed = Math.max(maxSpeed, sample.speed());
        }
        // A speed of 0 means the horse had not moved yet: there is no value to record, so none is filled.
        BigDecimal speed = BigDecimal.valueOf(maxSpeed).setScale(1, RoundingMode.HALF_UP);
        sessionService.fillSimulated(s.getId(), (int) Math.round((double) sum / (end + 1)),
                speed.signum() > 0 ? speed : null);
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private static int offset(Instant start, Instant at) {
        return (int) Math.max(0, Duration.between(start, at).getSeconds());
    }

    private Horse horse(Long horseId) {
        return horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
    }

    private String horseName(Long horseId) {
        return horses.findById(horseId).map(Horse::getName).orElse("?");
    }
}
