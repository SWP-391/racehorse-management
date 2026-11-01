package vn.racehorse.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;
import vn.racehorse.training.service.SessionService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fixtures for training tests: one account per role, horses in a given health state, plans and sessions. */
public abstract class TrainingTestBase extends ApiTest {

    public static final ZoneId CLUB = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    public SessionService sessionService;

    public AccountPrincipal manager;
    public AccountPrincipal vet;
    public AccountPrincipal trainer;
    public AccountPrincipal owner;

    private AccountPrincipal defaultGroom;

    /** The Groom named as responsible person by default (only an active Groom can be, BR-134). */
    public AccountPrincipal defaultGroom() {
        if (defaultGroom == null) {
            defaultGroom = account(Role.GROOM);
        }
        return defaultGroom;
    }

    @BeforeEach
    public void accounts() {
        defaultGroom = null;
        manager = account(Role.CLUB_MANAGER);
        vet = account(Role.VETERINARIAN);
        trainer = account(Role.HEAD_TRAINER);
        useTrainer(trainer);
        owner = account(Role.HORSE_OWNER);
    }

    public LocalDate today() {
        return sessionService.today();
    }

    /** Registered horse: Cần theo dõi with the intake lock, never examined. */
    public long newHorse() throws Exception {
        return registerHorse(manager, owner, "MALE");
    }

    /** Cần theo dõi, no lock. */
    public long observedHorse() throws Exception {
        long horse = newHorse();
        asJson(vet, post("/api/horses/" + horse + "/lock/lift"), Map.of("returnLevel", "RESUME_AS_IS"))
                .andExpect(status().isOk());
        return horse;
    }

    /** Đủ điều kiện, no lock. */
    public long eligibleHorse() throws Exception {
        long horse = observedHorse();
        setStatus(horse, "ELIGIBLE");
        return horse;
    }

    public void setStatus(long horse, String to) throws Exception {
        asJson(vet, post("/api/horses/" + horse + "/health/status"), Map.of("status", to, "reason", "khám: " + to))
                .andExpect(status().isOk());
    }

    public void lock(long horse, String reason) throws Exception {
        asJson(vet, post("/api/horses/" + horse + "/lock"), Map.of("reason", reason)).andExpect(status().isOk());
    }

    public Map<String, Object> phase(String name, LocalDate start, LocalDate end) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", name);
        p.put("startDate", start.toString());
        p.put("endDate", end.toString());
        p.put("goal", "Tăng sức bền");
        p.put("distanceM", 1600);
        p.put("weeklyVolumeKm", 12.5);
        p.put("surface", "Cỏ");
        return p;
    }

    public long draftPlan(long horse) throws Exception {
        return body(asJson(trainer, post("/api/horses/" + horse + "/plans"), Map.of("title", "Giáo án mùa thu",
                "phases", List.of(phase("Nền tảng", today(), today().plusDays(60)))))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    public long activePlan(long horse) throws Exception {
        long plan = draftPlan(horse);
        as(trainer, post("/api/plans/" + plan + "/activate")).andExpect(status().isOk());
        return plan;
    }

    private int slot;

    /**
     * Each session of a test gets its own start time, counting back from 23:00 in 61-minute steps, so fixtures never
     * overlap (BR-121, BR-122) and a session dated tomorrow or later is never a late change (BR-117).
     */
    public LocalTime nextStartTime() {
        return LocalTime.of(23, 0).minusMinutes(61L * slot++);
    }

    public Map<String, Object> sessionBody(LocalDate date, String type, boolean draft) {
        Map<String, Object> s = new HashMap<>();
        s.put("date", date.toString());
        s.put("startTime", nextStartTime().toString());
        s.put("type", type);
        s.put("plannedDurationMin", 60);
        s.put("targetDistanceM", 1200);
        s.put("targetSurface", "Cát");
        s.put("responsiblePersonId", defaultGroom().id());
        s.put("draft", draft);
        return s;
    }

    public long schedule(long plan, LocalDate date) throws Exception {
        return body(asJson(trainer, post("/api/plans/" + plan + "/sessions"), sessionBody(date, "GALLOP", false))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    public void start(long session, boolean ack) throws Exception {
        asJson(trainer, post("/api/sessions/" + session + "/start"), Map.of("acknowledgeWarning", ack))
                .andExpect(status().isOk());
    }

    public Map<String, Object> indicators() {
        Map<String, Object> i = new HashMap<>();
        i.put("distanceM", 1500);
        i.put("durationMin", 55);
        i.put("surface", "Cát");
        i.put("intensity", "MEDIUM");
        i.put("form", 4);
        i.put("comment", "Phản xạ tốt");
        return i;
    }

    public void end(long session) throws Exception {
        asJson(trainer, post("/api/sessions/" + session + "/end"), indicators()).andExpect(status().isOk());
    }

    public JsonNode session(long id) throws Exception {
        return body(as(trainer, get("/api/sessions/" + id)).andExpect(status().isOk())).get("session");
    }

    public String state(long session) throws Exception {
        return session(session).get("state").asText();
    }

    public JsonNode health(long horse) throws Exception {
        return body(as(vet, get("/api/horses/" + horse + "/health")).andExpect(status().isOk()));
    }

    public boolean hasNotification(AccountPrincipal who, String text) throws Exception {
        JsonNode inbox = body(as(who, get("/api/notifications")));
        for (JsonNode n : inbox.get("items")) {
            if (n.get("message").asText().contains(text)) {
                return true;
            }
        }
        return false;
    }

    public String horseName(long horse) throws Exception {
        return body(as(manager, get("/api/horses/" + horse))).get("name").asText();
    }
}
