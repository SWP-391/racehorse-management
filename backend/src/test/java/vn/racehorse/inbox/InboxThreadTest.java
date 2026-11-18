package vn.racehorse.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One chat per person (the Club Manager role counts as one) and the highlighted feedback to the club. */
class InboxThreadTest extends InboxTestBase {

    private JsonNode threads(AccountPrincipal who) throws Exception {
        return body(as(who, get("/api/inbox/threads?size=100")).andExpect(status().isOk()));
    }

    private JsonNode thread(AccountPrincipal who, String key) throws Exception {
        return body(as(who, get("/api/inbox/threads/" + key)).andExpect(status().isOk()));
    }

    private Map<String, Object> feedback(String subject, String text) {
        Map<String, Object> m = toManagers(subject, text);
        m.put("kind", "FEEDBACK");
        return m;
    }

    @Test
    void severalConversationsWithTheClubManagersAreOneChatForTheOwner() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm = account(Role.CLUB_MANAGER);
        long first = started(owner, toManagers("Đổi giờ cho ăn", "Sớm hơn 30 phút được không?"));
        long second = started(owner, toManagers("Hóa đơn tháng này", "Có vẻ sai"));
        send(cm, first, "Đã ghi nhận").andExpect(status().isCreated());

        JsonNode rows = threads(owner);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("key").asText()).isEqualTo("club");
        assertThat(rows.get(0).get("name").asText()).isEqualTo("Quản lý CLB");
        assertThat(rows.get(0).get("topics").asInt()).isEqualTo(2);
        assertThat(rows.get(0).get("unread").asLong()).isEqualTo(1);

        JsonNode chat = thread(owner, "club");
        assertThat(chat.get("topics")).hasSize(2);
        assertThat(chat.get("messages")).hasSize(3);
        assertThat(chat.get("messages").get(0).get("conversationId").asLong()).isEqualTo(first);
        assertThat(chat.get("messages").get(1).get("conversationId").asLong()).isEqualTo(second);
        // opening the whole chat reads every conversation in it
        assertThat(unread(owner)).isZero();
    }

    @Test
    void theClubManagerSeesOneChatPerOwnerWhetherTheOwnerOrTheManagerStartedIt() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm = account(Role.CLUB_MANAGER);
        started(owner, toManagers("Góp ý", "Cần thêm đèn"));
        started(cm, toPerson("Nhắc đóng phí", "Tháng này chưa đóng", owner, null));

        String key = "u" + owner.id();
        JsonNode mine = null;
        for (JsonNode row : threads(cm)) {
            if (row.get("key").asText().equals(key)) {
                mine = row;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("topics").asInt()).isEqualTo(2);
        // the owner sees the manager's own message in the same "Quản lý CLB" chat
        JsonNode ownerChat = thread(owner, "club");
        assertThat(ownerChat.get("topics")).hasSize(2);
        assertThat(threads(owner)).hasSize(1);
    }

    @Test
    void feedbackToTheClubIsMarkedAndOnlyItsFirstMessageIsHighlighted() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm = account(Role.CLUB_MANAGER);
        long id = started(owner, feedback("Cảm ơn đội chăm sóc", "Ngựa khỏe hơn nhiều"));
        send(cm, id, "Cảm ơn anh").andExpect(status().isCreated());

        JsonNode chat = thread(owner, "club");
        assertThat(chat.get("topics").get(0).get("kind").asText()).isEqualTo("FEEDBACK");
        assertThat(chat.get("messages").get(0).get("feedback").asBoolean()).isTrue();
        assertThat(chat.get("messages").get(1).get("feedback").asBoolean()).isFalse();
        assertThat(threads(owner).get(0).get("hasFeedback").asBoolean()).isTrue();
        assertThat(open(cm, id).get("kind").asText()).isEqualTo("FEEDBACK");
    }

    @Test
    void feedbackCanOnlyBeSentToTheClubByANonManager() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm = account(Role.CLUB_MANAGER);
        Map<String, Object> toPerson = toPerson("x", "y", cm, null);
        toPerson.put("kind", "FEEDBACK");
        start(owner, toPerson).andExpect(status().isBadRequest());
        start(cm, feedback("x", "y")).andExpect(status().isBadRequest());
        Map<String, Object> odd = toManagers("x", "y");
        odd.put("kind", "SHOUT");
        start(owner, odd).andExpect(status().isBadRequest());
    }

    @Test
    void aMessageIntoAChatGoesToItsOpenConversationAndRefusesWhenAllAreClosed() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal cm = account(Role.CLUB_MANAGER);
        long id = started(owner, toManagers("Một", "a"));
        asJson(owner, post("/api/inbox/threads/club/messages"), Map.of("text", "tiếp")).andExpect(status().isCreated());
        assertThat(open(owner, id).get("messages")).hasSize(2);

        as(cm, post("/api/inbox/conversations/" + id + "/close")).andExpect(status().isNoContent());
        asJson(owner, post("/api/inbox/threads/club/messages"), Map.of("text", "còn không")).andExpect(status().isConflict());
        assertThat(thread(owner, "club").get("openTopicId").isNull()).isTrue();
    }

    @Test
    void aChatThatDoesNotExistOrIsSomeoneElsesIsNotFound() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal stranger = account(Role.HORSE_OWNER);
        started(owner, toManagers("Riêng", "tư"));
        as(stranger, get("/api/inbox/threads/u" + owner.id())).andExpect(status().isNotFound());
        as(stranger, get("/api/inbox/threads/abc")).andExpect(status().isNotFound());
        asJson(stranger, post("/api/inbox/threads/u" + owner.id() + "/messages"), new LinkedHashMap<>(Map.of("text", "x")))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchNarrowsTheChatsAndTheListIsPaged() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        started(owner, toManagers("Chủ đề đặc biệt xyz", "a"));
        assertThat(pageBody(as(owner, get("/api/inbox/threads?q=xyz")).andExpect(status().isOk())).get("total").asInt()).isEqualTo(1);
        assertThat(pageBody(as(owner, get("/api/inbox/threads?q=khongco")).andExpect(status().isOk())).get("total").asInt()).isZero();
        assertThat(body(as(owner, get("/api/inbox/threads?page=5&size=1")).andExpect(status().isOk()))).isEmpty();
    }
}
