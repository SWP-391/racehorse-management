package vn.racehorse.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fixtures for the inbox tests: horses with an owner and a trainer, examinations written straight into the table. */
public abstract class InboxTestBase extends ApiTest {

    @Autowired
    protected JdbcTemplate jdbc;

    /** A horse of the owner assigned to the trainer, registered through the API by a Club Manager. */
    protected long horseOf(AccountPrincipal owner, AccountPrincipal trainer) throws Exception {
        useTrainer(trainer);
        return registerHorse(account(Role.CLUB_MANAGER), owner, "MALE");
    }

    protected void examine(long horseId, AccountPrincipal vet) {
        jdbc.update("INSERT INTO examination (horse_id, exam_date, diagnosis, recorded_by_id, recorded_at) "
                + "VALUES (?, current_date, 'kiểm tra', ?, now())", horseId, vet.id());
    }

    protected void lend(long horseId, AccountPrincipal temp, int days) {
        jdbc.update("UPDATE horse SET temp_trainer_id = ?, temp_until = current_date + ? WHERE id = ?",
                temp.id(), days, horseId);
    }

    protected void archive(long horseId) {
        jdbc.update("UPDATE horse SET archived_at = now(), archive_reason = 'RETIRED' WHERE id = ?", horseId);
    }

    protected Map<String, Object> toPerson(String subject, String text, AccountPrincipal to, Long horseId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subject", subject);
        m.put("text", text);
        m.put("recipientId", to.id());
        m.put("horseId", horseId);
        return m;
    }

    protected Map<String, Object> toManagers(String subject, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subject", subject);
        m.put("text", text);
        m.put("toClubManagers", true);
        return m;
    }

    protected ResultActions start(AccountPrincipal who, Map<String, Object> body) throws Exception {
        return asJson(who, post("/api/inbox/conversations"), body);
    }

    protected long started(AccountPrincipal who, Map<String, Object> body) throws Exception {
        return body(start(who, body).andExpect(status().isCreated())).get("id").asLong();
    }

    protected ResultActions send(AccountPrincipal who, long conversation, String text) throws Exception {
        return asJson(who, post("/api/inbox/conversations/" + conversation + "/messages"), Map.of("text", text));
    }

    protected JsonNode open(AccountPrincipal who, long conversation) throws Exception {
        return body(as(who, get("/api/inbox/conversations/" + conversation)).andExpect(status().isOk()));
    }

    protected JsonNode list(AccountPrincipal who) throws Exception {
        return body(as(who, get("/api/inbox/conversations")).andExpect(status().isOk()));
    }

    protected long unread(AccountPrincipal who) throws Exception {
        return body(as(who, get("/api/inbox/unread")).andExpect(status().isOk())).get("unread").asLong();
    }

    protected long unreadNotices(AccountPrincipal who) throws Exception {
        return pageBody(as(who, get("/api/notifications")).andExpect(status().isOk())).get("unread").asLong();
    }
}
