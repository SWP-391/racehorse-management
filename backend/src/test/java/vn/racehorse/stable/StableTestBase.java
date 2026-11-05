package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.TestClock;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Fixtures for stable-care tests: a Groom, marking, the chore list and audit entries of one actor. */
abstract class StableTestBase extends TrainingTestBase {

    AccountPrincipal groom;

    @BeforeEach
    void groomAccount() {
        groom = account(Role.GROOM);
    }

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    ResultActions mark(AccountPrincipal who, long horse, String chore) throws Exception {
        return as(who, post("/api/horses/" + horse + "/chores/" + chore + "/done"));
    }

    ResultActions cancel(AccountPrincipal who, long entryId, String reason) throws Exception {
        return asJson(who, post("/api/chore-entries/" + entryId + "/cancel"), reason == null ? Map.of() : Map.of("reason", reason));
    }

    JsonNode day(AccountPrincipal who, String date) throws Exception {
        var request = get("/api/chores");
        if (date != null) {
            request.param("date", date);
        }
        return body(as(who, request));
    }

    /** The row of one horse in a chore list, or null when the horse is not on it. */
    static JsonNode row(JsonNode day, long horse) {
        for (JsonNode h : day.get("horses")) {
            if (h.get("horseId").asLong() == horse) {
                return h;
            }
        }
        return null;
    }

    static JsonNode chore(JsonNode row, String chore) {
        for (JsonNode c : row.get("chores")) {
            if (c.get("chore").asText().equals(chore)) {
                return c;
            }
        }
        return null;
    }

    boolean done(AccountPrincipal who, long horse, String chore) throws Exception {
        return chore(row(day(who, null), horse), chore).get("done").asBoolean();
    }

    List<String> auditActions(AccountPrincipal actor) throws Exception {
        List<String> actions = new ArrayList<>();
        for (JsonNode e : body(as(manager, get("/api/audit").param("actorId", actor.id().toString())))) {
            actions.add(e.get("action").asText());
        }
        return actions;
    }

    /** Moves the clock to a club-local date and time. */
    static LocalDate shiftTo(int daysAhead, LocalTime time) {
        LocalDate date = LocalDate.now(CLUB).plusDays(daysAhead);
        TestClock.shift(Duration.between(Instant.now(), date.atTime(time).atZone(CLUB).toInstant()));
        return date;
    }

    /** Creates an area through the API as the Club Manager and returns its id. */
    long newArea(String name, int capacity) throws Exception {
        return body(asJson(manager, post("/api/areas"), Map.of("name", name, "capacity", capacity))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()))
                .get("id").asLong();
    }

    ResultActions place(AccountPrincipal who, long horse, long area, int slot) throws Exception {
        return asJson(who, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/horses/" + horse + "/stall"),
                Map.of("areaId", area, "slot", slot));
    }

    void archive(long horse) throws Exception {
        asJson(manager, post("/api/horses/" + horse + "/archive"), Map.of("reason", "SOLD"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }
}
