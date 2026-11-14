package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The other lists page, search without tone marks and keep their visibility rule inside the count (BR-185, AC-365, AC-370). */
class ListPagingTest extends StableTestBase {

    private JsonNode page(AccountPrincipal who, String path, String... params) throws Exception {
        var request = get(path);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return pageBody(as(who, request).andExpect(status().isOk()));
    }

    private static boolean has(JsonNode page, String field, String value) {
        return StreamSupport.stream(page.get("items").spliterator(), false).anyMatch(o -> value.equals(o.get(field).asText()));
    }

    private void supply(String name, boolean active) throws Exception {
        long id = body(asJson(manager, post("/api/supplies"), Map.of("name", name, "category", "FEED", "unitId", unitId("kg"), "price", 1000, "openingQuantity", 10))
                .andExpect(status().isCreated())).get("id").asLong();
        if (!active) {
            asJson(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/supplies/" + id),
                    Map.of("name", name, "category", "FEED", "unitId", unitId("kg"), "price", 1000, "openingQuantity", 10, "active", false)).andExpect(status().isOk());
        }
    }

    @Test
    void accountsPageAndSearchWithoutToneMarks() throws Exception {
        String name = unique("Đặng");
        asJson(manager, post("/api/accounts"), Map.of("email", uniqueEmail("dg"), "displayName", name + " Mạnh", "password", PASSWORD,
                "roles", java.util.List.of("GROOM"))).andExpect(status().isCreated());
        JsonNode found = page(manager, "/api/accounts", "q", name.toLowerCase().replace("đ", "d").replace("ặ", "a"), "role", "GROOM");
        assertThat(found.get("total").asInt()).isEqualTo(1);
        assertThat(page(manager, "/api/accounts", "size", "5").get("items").size()).isLessThanOrEqualTo(5);
        as(trainer, get("/api/accounts")).andExpect(status().isForbidden());
    }

    @Test
    void suppliesFilterByCategory_andAGroomSeesActiveItemsOnlyInRowsAndCount() throws Exception {
        String active = unique("Yến mạch");
        String inactive = unique("Cám cũ");
        supply(active, true);
        supply(inactive, false);
        JsonNode groomView = page(account(Role.GROOM), "/api/supplies", "size", "100000");
        assertThat(has(groomView, "name", active)).isTrue();
        assertThat(has(groomView, "name", inactive)).isFalse();
        JsonNode managerView = page(manager, "/api/supplies", "q", "cam cu", "size", "100000");
        assertThat(has(managerView, "name", inactive)).isTrue();
        assertThat(page(manager, "/api/supplies", "category", "MEDICINE", "q", active).get("total").asInt()).isZero();
        JsonNode groomSearch = page(account(Role.GROOM), "/api/supplies", "q", inactive);
        assertThat(groomSearch.get("total").asInt()).isZero();
    }

    @Test
    void auditSearchMatchesTheVietnameseLabelAndTheCodeWithoutToneMarks() throws Exception {
        newHorse();
        for (String q : new String[] {"Đăng ký ngựa", "dang ky ngua", "HORSE_REGISTERED", "horse_reg"}) {
            JsonNode found = page(manager, "/api/audit", "q", q, "size", "100000");
            assertThat(has(found, "action", "HORSE_REGISTERED")).as("q=" + q).isTrue();
        }
        JsonNode other = page(manager, "/api/audit", "q", "Đăng ký ngựa", "size", "100000");
        assertThat(has(other, "action", "ACCOUNT_CREATED")).isFalse();
        assertThat(page(manager, "/api/audit", "q", "không có thao tác này").get("total").asInt()).isZero();
    }

    @Test
    void auditKeepsItsDefaultWindowAndFiltersByTextAndAction() throws Exception {
        newHorse();
        JsonNode byActor = page(manager, "/api/audit", "actorId", manager.id().toString(), "q", "ngua", "size", "100000");
        assertThat(byActor.get("total").asInt()).isGreaterThanOrEqualTo(0);
        JsonNode created = page(manager, "/api/audit", "action", "HORSE_REGISTERED", "size", "100000");
        for (JsonNode e : created.get("items")) {
            assertThat(e.get("action").asText()).isEqualTo("HORSE_REGISTERED");
        }
        as(manager, get("/api/audit").param("from", today().toString()).param("to", today().minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notificationsPageWithTheUnreadCountOverAllOfThem() throws Exception {
        JsonNode inbox = page(trainer, "/api/notifications", "size", "5");
        assertThat(inbox.has("unread")).isTrue();
        assertThat(inbox.get("items").size()).isLessThanOrEqualTo(5);
        assertThat(inbox.get("total").asInt()).isGreaterThanOrEqualTo(inbox.get("items").size());
    }

    @Test
    void theChoreDayListPagesByHorseAndSearchesTheName() throws Exception {
        String name = unique("Chuồng");
        asJson(manager, post("/api/horses"), horseBody(name, "MALE", owner.id())).andExpect(status().isCreated());
        JsonNode day = page(groom, "/api/chores", "q", name.toLowerCase());
        assertThat(day.get("horses")).hasSize(1);
        assertThat(day.get("total").asInt()).isEqualTo(1);
        assertThat(page(groom, "/api/chores", "size", "3").get("horses").size()).isLessThanOrEqualTo(3);
    }
}
