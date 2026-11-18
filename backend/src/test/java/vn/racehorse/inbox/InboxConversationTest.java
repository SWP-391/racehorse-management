package vn.racehorse.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Start, send, read, privacy, unread and close of conversations (BR-227, BR-231 to BR-233; TECH-240 to TECH-243). */
class InboxConversationTest extends InboxTestBase {

    private long conversations() {
        return jdbc.queryForObject("SELECT count(*) FROM conversation", Long.class);
    }

    private long messages() {
        return jdbc.queryForObject("SELECT count(*) FROM message", Long.class);
    }

    @Test
    void AC_487_anOwnerWritesToTheClubManagersAndEveryActiveOneIsNotified() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm1 = account(Role.CLUB_MANAGER);
        AccountPrincipal cm2 = account(Role.CLUB_MANAGER);
        // a Club Manager sees every conversation addressed to the role, so older ones count too: measure the change
        long before = unread(cm1);

        long id = started(owner, toManagers("Xin đổi giờ cho ăn", "Cho ăn sớm hơn 30 phút được không?"));

        assertThat(unreadNotices(cm1)).isEqualTo(1);
        assertThat(unreadNotices(cm2)).isEqualTo(1);
        assertThat(unread(cm1)).isEqualTo(before + 1);
        assertThat(unreadNotices(owner)).isZero();
        JsonNode mine = list(owner);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("id").asLong()).isEqualTo(id);
        assertThat(mine.get(0).get("with").asText()).isEqualTo("Quản lý CLB");
        assertThat(mine.get(0).get("unread").asLong()).isZero();
        JsonNode seenByCm = null;
        for (JsonNode row : list(cm1)) {
            if (row.get("id").asLong() == id) {
                seenByCm = row;
            }
        }
        assertThat(seenByCm).isNotNull();
        assertThat(seenByCm.get("with").asText()).startsWith("Test horse_owner");
    }

    @Test
    void AC_488_anyClubManagerAnswersAndTheOwnerIsNotifiedUntilTheyOpenIt() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm1 = account(Role.CLUB_MANAGER);
        AccountPrincipal cm2 = account(Role.CLUB_MANAGER);
        long id = started(owner, toManagers("Góp ý", "Cần thêm đèn ở chuồng"));
        long cm1Before = unread(cm1);
        long cm2Before = unread(cm2);

        send(cm1, id, "Đã ghi nhận, sẽ lắp trong tuần").andExpect(status().isCreated());

        assertThat(unread(owner)).isEqualTo(1);
        assertThat(unreadNotices(owner)).isEqualTo(1);
        // the sender gets no notice and no unread mark for their own message; the other Club Manager still has one
        assertThat(unread(cm1)).isEqualTo(cm1Before - 1);
        assertThat(unread(cm2)).isEqualTo(cm2Before + 1);
        JsonNode seen = open(cm2, id);
        assertThat(seen.get("messages")).hasSize(2);
        assertThat(seen.get("messages").get(1).get("text").asText()).isEqualTo("Đã ghi nhận, sẽ lắp trong tuần");

        JsonNode opened = open(owner, id);
        assertThat(opened.get("messages").get(1).get("mine").asBoolean()).isFalse();
        assertThat(unread(owner)).isZero();
        assertThat(unreadNotices(owner)).isZero();
    }

    @Test
    void AC_496_outsidersAreToldTheConversationDoesNotExist() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal trainer = account(Role.HEAD_TRAINER);
        long horse = horseOf(owner, trainer);
        long id = started(owner, toPerson("Riêng tư", "Chỉ hai chúng ta", trainer, horse));

        for (AccountPrincipal outsider : new AccountPrincipal[] {account(Role.CLUB_MANAGER), account(Role.HORSE_OWNER),
                account(Role.HEAD_TRAINER), account(Role.VETERINARIAN), account(Role.GROOM)}) {
            as(outsider, get("/api/inbox/conversations/" + id)).andExpect(status().isNotFound());
            send(outsider, id, "chen ngang").andExpect(status().isNotFound());
            asJson(outsider, post("/api/inbox/conversations/" + id + "/close"), Map.of()).andExpect(status().isNotFound());
            for (JsonNode row : list(outsider)) {
                assertThat(row.get("id").asLong()).isNotEqualTo(id);
            }
        }
        assertThat(open(owner, id).get("messages")).hasSize(1);
    }

    @Test
    void theClubManagerReadsOnlyWhatIsAddressedToTheRoleOrToThem() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm = account(Role.CLUB_MANAGER);
        AccountPrincipal groom = account(Role.GROOM);
        long toRole = started(owner, toManagers("Cho quản lý", "x"));
        long toCm = started(groom, toManagers("Cho quản lý", "y"));
        long toMe = started(account(Role.CLUB_MANAGER), toPerson("Gửi riêng", "z", groom, null));

        assertThat(list(cm)).extracting(n -> n.get("id").asLong()).contains(toRole, toCm).doesNotContain(toMe);
        as(cm, get("/api/inbox/conversations/" + toMe)).andExpect(status().isNotFound());
        // two people of the same role who are not in the conversation see nothing of it
        as(account(Role.GROOM), get("/api/inbox/conversations/" + toCm)).andExpect(status().isNotFound());
    }

    @Test
    void AC_498_lengthsAreEnforcedAfterTrimming() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long before = conversations();
        long beforeMessages = messages();

        start(owner, toManagers("   ", "nội dung")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.subject").exists());
        start(owner, toManagers("x".repeat(121), "nội dung")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.subject").exists());
        start(owner, toManagers("tiêu đề", "   ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.text").exists());
        start(owner, toManagers("tiêu đề", "x".repeat(2001))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.text").exists());
        assertThat(conversations()).isEqualTo(before);
        assertThat(messages()).isEqualTo(beforeMessages);

        long id = started(owner, toManagers("x".repeat(120), "y".repeat(2000)));
        send(owner, id, " ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.text").exists());
        send(owner, id, "z".repeat(2001)).andExpect(status().isBadRequest());
        send(owner, id, "  " + "z".repeat(2000) + "  ").andExpect(status().isCreated());
        assertThat(open(owner, id).get("messages").get(1).get("text").asText()).hasSize(2000);
    }

    @Test
    void AC_499_aMessageCannotBeEditedOrDeleted() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long id = started(owner, toManagers("Giữ nguyên", "Lời gốc"));
        long messageId = open(owner, id).get("messages").get(0).get("id").asLong();
        String path = "/api/inbox/conversations/" + id + "/messages/" + messageId;

        asJson(owner, put(path), Map.of("text", "sửa")).andExpect(status().is4xxClientError());
        asJson(owner, patch(path), Map.of("text", "sửa")).andExpect(status().is4xxClientError());
        as(owner, delete(path)).andExpect(status().is4xxClientError());
        as(owner, delete("/api/inbox/conversations/" + id)).andExpect(status().is4xxClientError());
        assertThat(open(owner, id).get("messages").get(0).get("text").asText()).isEqualTo("Lời gốc");
    }

    @Test
    void AC_500_closingRefusesAnswersUntilReopened() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm = account(Role.CLUB_MANAGER);
        long id = started(owner, toManagers("Xong việc", "Cảm ơn"));

        asJson(cm, post("/api/inbox/conversations/" + id + "/close"), Map.of()).andExpect(status().isNoContent());

        JsonNode closed = open(owner, id);
        assertThat(closed.get("state").asText()).isEqualTo("CLOSED");
        assertThat(closed.get("stateBy").asText()).isEqualTo("Test " + cmEmail(cm));
        assertThat(closed.get("stateAt").isNull()).isFalse();
        send(owner, id, "còn nữa").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));
        assertThat(open(owner, id).get("messages")).hasSize(1);

        asJson(owner, post("/api/inbox/conversations/" + id + "/reopen"), Map.of()).andExpect(status().isNoContent());
        assertThat(open(cm, id).get("state").asText()).isEqualTo("OPEN");
        send(owner, id, "còn nữa").andExpect(status().isCreated());
        // closing twice is harmless
        asJson(owner, post("/api/inbox/conversations/" + id + "/reopen"), Map.of()).andExpect(status().isNoContent());
        asJson(account(Role.GROOM), post("/api/inbox/conversations/" + id + "/close"), Map.of())
                .andExpect(status().isNotFound());
    }

    private String cmEmail(AccountPrincipal who) {
        return jdbc.queryForObject("SELECT email FROM user_account WHERE id = ?", String.class, who.id());
    }

    @Test
    void AC_501_aLaterReassignmentDoesNotChangeTheParticipants() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal t1 = account(Role.HEAD_TRAINER);
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        long horse = horseOf(owner, t1);
        long id = started(owner, toPerson("Lịch tập", "Hỏi lịch", t1, horse));

        asJson(manager, put("/api/horses/" + horse + "/trainer"), Map.of("trainerId", t2.id())).andExpect(status().isOk());

        send(t1, id, "Vẫn là tôi trả lời").andExpect(status().isCreated());
        as(t2, get("/api/inbox/conversations/" + id)).andExpect(status().isNotFound());
        assertThat(unread(t2)).isZero();
        assertThat(open(owner, id).get("messages")).hasSize(2);
    }

    @Test
    void AC_502_theUnreadCountFallsAsConversationsAreOpened() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm = account(Role.CLUB_MANAGER);
        long a = started(owner, toManagers("Một", "1"));
        long b = started(owner, toManagers("Hai", "2"));
        asJson(cm, post("/api/inbox/conversations/" + a + "/messages"), Map.of("text", "a1")).andExpect(status().isCreated());
        send(cm, a, "a2").andExpect(status().isCreated());
        send(cm, b, "b1").andExpect(status().isCreated());

        assertThat(unread(owner)).isEqualTo(3);
        JsonNode rows = list(owner);
        long inA = 0;
        for (JsonNode row : rows) {
            if (row.get("id").asLong() == a) {
                inA = row.get("unread").asLong();
            }
        }
        assertThat(inA).isEqualTo(2);
        open(owner, a);
        assertThat(unread(owner)).isEqualTo(1);
        open(owner, b);
        assertThat(unread(owner)).isZero();
        assertThat(unreadNotices(owner)).isZero();
        // the newest conversation comes first
        assertThat(list(owner).get(0).get("id").asLong()).isEqualTo(b);
    }

    @Test
    void aNoticeLinksToTheConversation() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);
        AccountPrincipal trainer = account(Role.HEAD_TRAINER);
        long id = started(groom, toPerson("Dọn chuồng", "Mấy giờ?", trainer, null));
        JsonNode notices = pageBody(as(trainer, get("/api/notifications")).andExpect(status().isOk())).get("items");
        assertThat(notices.get(0).get("link").asText()).isEqualTo("/inbox/" + id);
        assertThat(notices.get(0).get("message").asText()).contains("Dọn chuồng");
    }

    @Test
    void AC_504_aSignedOutVisitorIsRefusedAndNothingIsSaved() throws Exception {
        long before = conversations();
        long beforeMessages = messages();
        mvc.perform(get("/api/inbox/unread")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inbox/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inbox/recipients")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/inbox/conversations").contentType("application/json")
                .content(json.writeValueAsBytes(toManagers("a", "b")))).andExpect(status().is4xxClientError());
        assertThat(conversations()).isEqualTo(before);
        assertThat(messages()).isEqualTo(beforeMessages);
    }

    @Test
    void aMissingConversationIsNotFound() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        as(owner, get("/api/inbox/conversations/999999999")).andExpect(status().isNotFound());
        send(owner, 999999999L, "x").andExpect(status().isNotFound());
    }
}
