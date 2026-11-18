package vn.racehorse.inbox;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The constraints of V24 (TECH-239). */
class InboxMigrationTest extends InboxTestBase {

    private long conversation(AccountPrincipal by, String subject, String toRole, String state) {
        return jdbc.queryForObject("INSERT INTO conversation (subject, to_role, started_by_id, state, created_at) "
                + "VALUES (?, ?, ?, ?, now()) RETURNING id", Long.class, subject, toRole, by.id(), state);
    }

    @Test
    void aSubjectIsOneToOneHundredTwentyCharacters() {
        AccountPrincipal who = account(Role.HORSE_OWNER);
        conversation(who, "x".repeat(120), null, "OPEN");
        assertThatThrownBy(() -> conversation(who, "x".repeat(121), null, "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> conversation(who, "", null, "OPEN")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyTheClubManagerRoleCanBeAddressedAndTheStateIsOpenOrClosed() {
        AccountPrincipal who = account(Role.HORSE_OWNER);
        conversation(who, "a", "CLUB_MANAGER", "CLOSED");
        assertThatThrownBy(() -> conversation(who, "a", "GROOM", "OPEN")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> conversation(who, "a", null, "PAUSED")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aMessageIsOneToTwoThousandCharacters() {
        AccountPrincipal who = account(Role.HORSE_OWNER);
        long c = conversation(who, "a", null, "OPEN");
        String sql = "INSERT INTO message (conversation_id, author_id, body, sent_at) VALUES (?, ?, ?, now())";
        jdbc.update(sql, c, who.id(), "x".repeat(2000));
        assertThatThrownBy(() -> jdbc.update(sql, c, who.id(), "")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, c, who.id(), "x".repeat(2001)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneReadMarkAndOneParticipantRowPerPersonAndConversation() {
        AccountPrincipal who = account(Role.HORSE_OWNER);
        long c = conversation(who, "a", null, "OPEN");
        jdbc.update("INSERT INTO conversation_participant (conversation_id, account_id) VALUES (?, ?)", c, who.id());
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO conversation_participant (conversation_id, account_id) VALUES (?, ?)", c, who.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO conversation_read (conversation_id, account_id, last_read_message_id) VALUES (?, ?, 1)",
                c, who.id());
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO conversation_read (conversation_id, account_id, last_read_message_id) VALUES (?, ?, 2)",
                c, who.id())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
