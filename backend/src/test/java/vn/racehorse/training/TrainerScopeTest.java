package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every Head Trainer reads every horse; only the assigned one writes (BR-153, PERM-005 to PERM-008, PERM-013, PERM-014). */
class TrainerScopeTest extends TrainingTestBase {

    /** Head Trainer permissions that only read: every trainer holds them for every horse (BR-153). */
    private static final Set<Permission> READ_ONLY = EnumSet.of(Permission.VIEW_HORSES, Permission.VIEW_HORSE_OPTIONS,
            Permission.VIEW_LIVE, Permission.RECEIVE_ALERTS, Permission.VIEW_PROGRESS_BOARD, Permission.VIEW_PLANS,
            Permission.VIEW_EXAMINATIONS, Permission.VIEW_HEALTH, Permission.VIEW_READINESS,
            Permission.VIEW_INJURY_MARKS, Permission.WATCH_TRIAL_VIDEOS, Permission.VIEW_CHORES, Permission.VIEW_BARN,
            Permission.VIEW_RATION, Permission.VIEW_SCHEDULES, Permission.VIEW_OWN_TIMETABLE);

    /** Head Trainer permissions that write: guarded by AccessGuard.requireTrainerOf and listed in AC_291 below. */
    private static final Set<Permission> GUARDED = EnumSet.of(Permission.MANAGE_PLANS, Permission.SCHEDULE_SESSIONS,
            Permission.RUN_SESSIONS, Permission.SET_THRESHOLDS, Permission.REVIEW_BLOCKED_SESSIONS,
            Permission.REQUEST_LOCK_REVIEW, Permission.MANAGE_TRIAL_VIDEOS);

    private AccountPrincipal other;

    @BeforeEach
    void otherTrainer() {
        other = account(Role.HEAD_TRAINER);
    }

    @Test
    void AC_289_theAssignedTrainerWritesEveryStep() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long session = schedule(plan, today().plusDays(1));
        start(session, false);
        end(session);
        assertThat(state(session)).isEqualTo("COMPLETED");
    }

    @Test
    void AC_290_anotherTrainerReadsEverythingWithTheNameOfTheAssignedOne() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long session = schedule(plan, today().plusDays(2));

        JsonNode h = body(as(other, get("/api/horses/" + horse)).andExpect(status().isOk()));
        assertThat(h.get("canWrite").asBoolean()).isFalse();
        assertThat(h.get("assignedTrainer").get("displayName").asText()).isEqualTo(trainer.displayName());

        JsonNode row = null;
        for (JsonNode r : body(as(other, get("/api/progress")).andExpect(status().isOk()))) {
            if (r.get("horseId").asLong() == horse) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("canWrite").asBoolean()).isFalse();
        assertThat(row.get("assignedTrainer").get("id").asLong()).isEqualTo(trainer.id());

        JsonNode p = body(as(other, get("/api/plans/" + plan)).andExpect(status().isOk()));
        assertThat(p.get("canWrite").asBoolean()).isFalse();
        assertThat(p.get("assignedTrainer").get("id").asLong()).isEqualTo(trainer.id());
        as(other, get("/api/horses/" + horse + "/plans")).andExpect(status().isOk());
        as(other, get("/api/plans/" + plan + "/sessions")).andExpect(status().isOk());
        JsonNode s = body(as(other, get("/api/sessions/" + session)).andExpect(status().isOk())).get("session");
        assertThat(s.get("canWrite").asBoolean()).isFalse();
        as(other, get("/api/horses/" + horse + "/thresholds")).andExpect(status().isOk());
    }

    private ResultActions upload(AccountPrincipal who, long session) throws Exception {
        return mvc.perform(multipart("/api/sessions/" + session + "/videos")
                .file(new MockMultipartFile("file", "a.mp4", "video/mp4", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'}))
                .with(user(who)).with(csrf()));
    }

    @Test
    void AC_291_anotherTrainerCannotWriteAnything() throws Exception {
        long horse = eligibleHorse();
        long plan = activePlan(horse);
        long draftPlan = draftPlan(horse);
        long scheduled = schedule(plan, today().plusDays(2));
        Map<String, Object> draft = sessionBody(today().plusDays(3), "TROT", true);
        long drafted = body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), draft)
                .andExpect(status().isCreated())).get("id").asLong();
        long runningHorse = eligibleHorse();
        long running = schedule(activePlan(runningHorse), today());
        start(running, false);
        Map<String, Object> phases = Map.of("title", "x", "phases", List.of(phase("N", today(), today().plusDays(9))));

        Map<String, Callable<ResultActions>> writes = new LinkedHashMap<>();
        writes.put("create plan", () -> asJson(other, post("/api/horses/" + horse + "/plans"), phases));
        writes.put("edit plan", () -> asJson(other, put("/api/plans/" + plan), phases));
        writes.put("activate plan", () -> as(other, post("/api/plans/" + draftPlan + "/activate")));
        writes.put("complete plan", () -> as(other, post("/api/plans/" + plan + "/complete")));
        writes.put("cancel plan", () -> asJson(other, post("/api/plans/" + plan + "/cancel"), Map.of("reason", "x")));
        writes.put("keep after lock", () -> as(other, post("/api/plans/" + plan + "/review/keep")));
        writes.put("shift", () -> asJson(other, post("/api/plans/" + plan + "/shift"), Map.of("days", 1, "dryRun", true)));
        writes.put("restore version", () -> as(other, post("/api/plans/" + plan + "/versions/1/restore")));
        writes.put("create session", () -> asJson(other, post("/api/plans/" + plan + "/sessions"),
                sessionBody(today().plusDays(5), "TROT", true)));
        writes.put("schedule draft", () -> as(other, post("/api/sessions/" + drafted + "/schedule")));
        writes.put("change responsible", () -> asJson(other, put("/api/sessions/" + scheduled + "/responsible"),
                Map.of("responsiblePersonId", defaultGroom().id())));
        writes.put("cancel session", () -> asJson(other, post("/api/sessions/" + scheduled + "/cancel"),
                Map.of("reason", "x")));
        writes.put("start session", () -> asJson(other, post("/api/sessions/" + scheduled + "/start"), Map.of()));
        writes.put("end session", () -> asJson(other, post("/api/sessions/" + running + "/end"), indicators()));
        writes.put("complete after auto close", () -> asJson(other, put("/api/sessions/" + running + "/indicators"),
                indicators()));
        writes.put("set threshold", () -> asJson(other, put("/api/horses/" + horse + "/thresholds"),
                Map.of("measure", "HEART_RATE", "limit", 190)));
        writes.put("lock review note", () -> asJson(other, post("/api/horses/" + horse + "/lock/review-notes"),
                Map.of("note", "x")));
        writes.put("upload trial video", () -> upload(other, scheduled));

        for (Map.Entry<String, Callable<ResultActions>> w : writes.entrySet()) {
            try {
                w.getValue().call().andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_TRAINER"));
            } catch (AssertionError e) {
                throw new AssertionError("write refused as another trainer: " + w.getKey(), e);
            }
        }
        // Nothing changed.
        assertThat(state(scheduled)).isEqualTo("SCHEDULED");
        assertThat(state(running)).isEqualTo("IN_PROGRESS");
        assertThat(state(drafted)).isEqualTo("DRAFT");
        assertThat(body(as(trainer, get("/api/plans/" + plan + "/sessions"))).size()).isEqualTo(2);
        assertThat(body(as(trainer, get("/api/plans/" + draftPlan))).get("state").asText()).isEqualTo("DRAFT");
    }

    @Test
    void AC_291_everyWritePermissionOfTheHeadTrainerIsClassified() {
        Set<Permission> held = Arrays.stream(Permission.values()).filter(p -> p.allows(Role.HEAD_TRAINER))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
        held.removeAll(GUARDED);
        held.removeAll(READ_ONLY);
        assertThat(held)
                .as("a new Head Trainer permission: guard its writes with AccessGuard.requireTrainerOf and add the "
                        + "calls to AC_291, or list it as read-only here")
                .isEmpty();
    }
}
