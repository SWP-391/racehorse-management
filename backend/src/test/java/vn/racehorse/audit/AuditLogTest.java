package vn.racehorse.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Audit log: append-only storage and the Club Manager's filtered, read-only view (BR-092, BR-093). */
class AuditLogTest extends ApiTest {

    @Autowired
    JdbcTemplate jdbc;

    private long createAccountAs(AccountPrincipal manager) throws Exception {
        return body(asJson(manager, post("/api/accounts"), Map.of(
                "username", unique("user"), "displayName", "Người dùng", "password", PASSWORD,
                "roles", List.of("GROOM"))).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void databaseRejectsUpdateAndDeleteOfAuditEntries() throws Exception {
        createAccountAs(account(Role.CLUB_MANAGER));
        Long id = jdbc.queryForObject("select max(id) from audit_entry", Long.class);
        assertThatThrownBy(() -> jdbc.update("update audit_entry set action = 'X' where id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from audit_entry where id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select action from audit_entry where id = ?", String.class, id))
                .isEqualTo("ACCOUNT_CREATED");
    }

    @Test
    void AC_026_auditEntryCannotBeChangedOrRemovedThroughTheService() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        createAccountAs(manager);
        Long id = jdbc.queryForObject("select max(id) from audit_entry", Long.class);

        as(manager, put("/api/audit/" + id)).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("AUDIT_IMMUTABLE"));
        as(manager, patch("/api/audit/" + id)).andExpect(status().isMethodNotAllowed());
        as(manager, delete("/api/audit/" + id)).andExpect(status().isMethodNotAllowed());
        assertThat(jdbc.queryForObject("select count(*) from audit_entry where id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void AC_027_headTrainerCannotOpenTheAuditLog() throws Exception {
        as(account(Role.HEAD_TRAINER), get("/api/audit"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void filtersByUserAndByClubDate() throws Exception {
        AccountPrincipal first = account(Role.CLUB_MANAGER);
        AccountPrincipal second = account(Role.CLUB_MANAGER);
        createAccountAs(first);
        createAccountAs(second);
        createAccountAs(second);
        String today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

        JsonNode byFirst = body(as(first, get("/api/audit").param("actorId", first.id().toString())
                .param("from", today).param("to", today)).andExpect(status().isOk()));
        List<JsonNode> entries = StreamSupport.stream(byFirst.spliterator(), false).toList();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("actorId").asLong()).isEqualTo(first.id());
        assertThat(entries.get(0).get("action").asText()).isEqualTo("ACCOUNT_CREATED");

        JsonNode bySecond = body(as(first, get("/api/audit").param("actorId", second.id().toString())));
        assertThat(bySecond.size()).isEqualTo(2);

        String tomorrow = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).toString();
        JsonNode future = body(as(first, get("/api/audit").param("actorId", second.id().toString())
                .param("from", tomorrow).param("to", tomorrow)));
        assertThat(future.size()).isZero();
    }

    /** The log is paged in the database: a range with more than 5000 rows keeps an exact total and reaches the last page. */
    @Test
    void aLogOfMoreThanFiveThousandRowsKeepsAnExactTotalAndEveryPage() throws Exception {
        String actor = unique("Bulk");
        jdbc.batchUpdate("INSERT INTO audit_entry (actor_name, occurred_at, action, target_type) VALUES (?, now(), 'HORSE_REGISTERED', 'HORSE')",
                java.util.stream.IntStream.range(0, 5105).mapToObj(i -> new Object[] {actor}).toList());
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        JsonNode first = pageBody(as(manager, get("/api/audit").param("q", actor).param("size", "100")).andExpect(status().isOk()));
        assertThat(first.get("total").asInt()).isEqualTo(5105);
        assertThat(first.get("items").size()).isEqualTo(100);
        JsonNode last = pageBody(as(manager, get("/api/audit").param("q", actor).param("size", "100").param("page", "51")));
        assertThat(last.get("items").size()).isEqualTo(5);
        JsonNode byLabel = pageBody(as(manager, get("/api/audit").param("q", "dang ky ngua").param("actorId", "0").param("size", "20")));
        assertThat(byLabel.get("total").asInt()).isZero();
    }
}
