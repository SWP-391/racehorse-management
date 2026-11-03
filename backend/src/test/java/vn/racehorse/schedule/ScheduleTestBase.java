package vn.racehorse.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fixtures for the Groom schedule and leave tests: Grooms created through the API with barn hours. */
public abstract class ScheduleTestBase extends TrainingTestBase {

    /** A schedule entry: ISO weekday and "HH:mm" strings. */
    protected static Map<String, Object> range(int weekday, String start, String end) {
        Map<String, Object> r = new HashMap<>();
        r.put("weekday", weekday);
        r.put("start", start);
        r.put("end", end);
        return r;
    }

    /** The same hours on the weekday of the date. */
    protected static Map<String, Object> hoursOn(LocalDate date, String start, String end) {
        return range(date.getDayOfWeek().getValue(), start, end);
    }

    protected ResultActions createGroom(AccountPrincipal who, List<Map<String, Object>> schedule) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", unique("groom"));
        body.put("displayName", "Groom " + System.nanoTime());
        body.put("password", PASSWORD);
        body.put("roles", List.of("GROOM"));
        if (schedule != null) {
            body.put("schedule", schedule);
        }
        return asJson(who, post("/api/accounts"), body);
    }

    protected long newGroom(List<Map<String, Object>> schedule) throws Exception {
        return body(createGroom(manager, schedule).andExpect(status().isCreated())).get("id").asLong();
    }

    protected AccountPrincipal groomPrincipal(long id) {
        return AccountPrincipal.of(accounts.findById(id).orElseThrow());
    }

    protected ResultActions setSchedule(AccountPrincipal who, long groom, List<Map<String, Object>> ranges)
            throws Exception {
        return asJson(who, put("/api/accounts/" + groom + "/schedule"), Map.of("ranges", ranges));
    }

    protected ResultActions recordLeave(AccountPrincipal who, long groom, LocalDate date) throws Exception {
        return asJson(who, post("/api/accounts/" + groom + "/leave"), Map.of("date", date.toString()));
    }

    protected List<String> rangesOf(JsonNode schedule) {
        List<String> out = new ArrayList<>();
        for (JsonNode r : schedule.get("ranges")) {
            out.add(r.get("weekday").asInt() + " " + r.get("start").asText() + "-" + r.get("end").asText());
        }
        return out;
    }
}
