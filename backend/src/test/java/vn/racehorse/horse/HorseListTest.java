package vn.racehorse.horse;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.TrainingTestBase;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The horse list is paged, searchable and filterable and never reveals another owner's horse (BR-185, AC-365 to AC-367, AC-370). */
class HorseListTest extends TrainingTestBase {

    private JsonNode page(AccountPrincipal who, String... params) throws Exception {
        var request = get("/api/horses");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return pageBody(as(who, request).andExpect(status().isOk()));
    }

    private long horseNamed(String name, AccountPrincipal forOwner) throws Exception {
        return body(asJson(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/horses"),
                horseBody(name, "MALE", forOwner.id())).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void AC_365_twentyFiveHorsesAreTwentyRowsAndAFurtherPage() throws Exception {
        AccountPrincipal o = account(Role.HORSE_OWNER);
        for (int i = 0; i < 25; i++) {
            horseNamed("Phân trang " + i, o);
        }
        JsonNode first = page(o, "page", "0", "size", "20");
        JsonNode second = page(o, "page", "1", "size", "20");
        assertThat(first.get("items")).hasSize(20);
        assertThat(first.get("total").asInt()).isEqualTo(25);
        assertThat(second.get("items")).hasSize(5);
        assertThat(first.get("items").get(0).get("id").asLong()).isNotEqualTo(second.get("items").get(0).get("id").asLong());
    }

    @Test
    void AC_439_theListFiltersByReadinessForTrainingAndShowsTheHealthStatusToTheRolesThatMayRead() throws Exception {
        long ready = eligibleHorse(); // Đủ điều kiện, no lock: READY
        long locked = newHorse(); // Cần theo dõi with the intake lock: not READY

        // The database is shared with every other test, so the horse may be on a later page: read all pages.
        assertThat(allIds(trainer, "readiness", "READY")).contains(ready).doesNotContain(locked);
        assertThat(allIds(trainer, "readiness", "UNDER_OBSERVATION")).doesNotContain(ready);

        boolean seen = false;
        for (JsonNode h : allItems(trainer)) {
            if (h.get("id").asLong() == ready) {
                assertThat(h.get("healthStatus").asText()).isEqualTo("ELIGIBLE");
                seen = true;
            }
        }
        assertThat(seen).isTrue();
        // An Owner reads no health status in the list and is refused the readiness filter like the health filter (PERM-020).
        for (JsonNode h : page(owner, "size", "100").get("items")) {
            assertThat(h.get("healthStatus").isNull()).isTrue();
        }
    }

    private java.util.List<JsonNode> allItems(AccountPrincipal who, String... params) throws Exception {
        java.util.List<JsonNode> items = new java.util.ArrayList<>();
        for (int p = 0; ; p++) {
            String[] with = java.util.Arrays.copyOf(params, params.length + 4);
            with[params.length] = "size";
            with[params.length + 1] = "100";
            with[params.length + 2] = "page";
            with[params.length + 3] = String.valueOf(p);
            JsonNode page = page(who, with);
            page.get("items").forEach(items::add);
            if (items.size() >= page.get("total").asInt() || page.get("items").isEmpty()) return items;
        }
    }

    private java.util.Set<Long> allIds(AccountPrincipal who, String... params) throws Exception {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        allItems(who, params).forEach(h -> ids.add(h.get("id").asLong()));
        return ids;
    }

    @Test
    void AC_365_searchIgnoresCaseAndToneMarks() throws Exception {
        String unique = unique("Sấm").replace("-", " ");
        long id = horseNamed(unique + " Chớp", owner);
        for (String q : new String[] {unique.toUpperCase(), "sam " + unique.substring(4).toLowerCase() + " chop"}) {
            JsonNode found = page(manager, "q", q, "size", "100").get("items");
            assertThat(java.util.stream.StreamSupport.stream(found.spliterator(), false).map(n -> n.get("id").asLong()))
                    .contains(id);
        }
    }

    @Test
    void AC_366_aSearchThatMatchesNothingIsAnEmptyPage() throws Exception {
        JsonNode none = page(manager, "q", unique("khong-co"));
        assertThat(none.get("items")).isEmpty();
        assertThat(none.get("total").asInt()).isZero();
    }

    @Test
    void AC_367_theFiltersNarrowAndPageTogether() throws Exception {
        long id = newHorse(); // a new horse is Cần theo dõi with the intake lock
        String areaName = unique("Khu Z");
        long areaId = body(asJson(manager, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/areas"),
                Map.of("name", areaName, "capacity", 9)).andExpect(status().isCreated())).get("id").asLong();
        asJson(manager, put("/api/horses/" + id + "/stall"), Map.of("areaId", areaId, "slot", 9)).andExpect(status().is2xxSuccessful());
        JsonNode byArea = page(manager, "area", areaName.toLowerCase(), "size", "100");
        assertThat(byArea.get("total").asInt()).isGreaterThanOrEqualTo(1);
        for (JsonNode h : byArea.get("items")) {
            assertThat(h.get("id").asLong()).isNotNull();
        }
        JsonNode observed = page(manager, "status", "UNDER_OBSERVATION", "area", areaName, "size", "100").get("items");
        JsonNode injured = page(manager, "status", "INJURED", "area", areaName, "size", "100").get("items");
        assertThat(java.util.stream.StreamSupport.stream(observed.spliterator(), false).map(n -> n.get("id").asLong())).contains(id);
        assertThat(java.util.stream.StreamSupport.stream(injured.spliterator(), false).map(n -> n.get("id").asLong())).doesNotContain(id);
    }

    @Test
    void AC_370_anOwnerSeesOnlyTheirOwnHorsesInRowsAndCount() throws Exception {
        AccountPrincipal other = account(Role.HORSE_OWNER);
        AccountPrincipal mine = account(Role.HORSE_OWNER);
        for (int i = 0; i < 3; i++) {
            horseNamed("Của tôi " + i, mine);
        }
        for (int i = 0; i < 4; i++) {
            horseNamed("Của người khác " + i, other);
        }
        JsonNode all = page(mine);
        assertThat(all.get("total").asInt()).isEqualTo(3);
        assertThat(all.get("items")).hasSize(3);
        JsonNode searched = page(mine, "q", "người khác");
        assertThat(searched.get("total").asInt()).isZero();
        assertThat(searched.get("items")).isEmpty();
        as(mine, get("/api/horses").param("status", "INJURED")).andExpect(status().isForbidden());
    }

    @Test
    void aSizeOutsideTheBoundsIsRefusedNamingSize() throws Exception {
        // The test run raises the largest page to 100000; the production limit of 100 is checked in PagingTest.
        as(manager, get("/api/horses").param("size", "100001")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.size").exists());
        as(manager, get("/api/horses").param("size", "0")).andExpect(status().isBadRequest());
    }
}
