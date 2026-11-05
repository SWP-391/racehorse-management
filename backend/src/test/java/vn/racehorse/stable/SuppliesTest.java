package vn.racehorse.stable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Supply catalog and restock proposals (BR-130 to BR-132). */
class SuppliesTest extends StableTestBase {

    private Map<String, Object> item(String name, String category, Object quantity) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("category", category);
        m.put("unitId", unitId("kg"));
        m.put("price", 25000);
        m.put("openingQuantity", quantity);
        return m;
    }

    private JsonNode create(String name, String category, Object quantity) throws Exception {
        return body(asJson(manager, post("/api/supplies"), item(name, category, quantity)).andExpect(status().isCreated()));
    }

    private JsonNode propose(AccountPrincipal who, long itemId, Object quantity) throws Exception {
        return body(asJson(who, post("/api/supplies/" + itemId + "/proposals"),
                Map.of("quantity", quantity, "note", "sắp hết")).andExpect(status().isCreated()));
    }

    private JsonNode itemById(AccountPrincipal who, long id) throws Exception {
        for (JsonNode i : body(as(who, get("/api/supplies")).andExpect(status().isOk()))) {
            if (i.get("id").asLong() == id) return i;
        }
        return null;
    }

    private JsonNode proposalById(AccountPrincipal who, long id) throws Exception {
        for (JsonNode p : body(as(who, get("/api/restock-proposals")).andExpect(status().isOk()))) {
            if (p.get("id").asLong() == id) return p;
        }
        return null;
    }

    @Test
    void AC_223_aNewItemAppearsInTheCatalogAndIsAudited() throws Exception {
        String name = unique("Yến mạch");
        JsonNode created = create(name, "FEED", 120);
        assertThat(created.get("name").asText()).isEqualTo(name);
        assertThat(created.get("category").asText()).isEqualTo("FEED");
        assertThat(created.get("unit").asText()).isEqualTo("kg");
        assertThat(created.get("quantity").asLong()).isEqualTo(120);
        assertThat(created.get("active").asBoolean()).isTrue();
        assertThat(itemById(manager, created.get("id").asLong())).isNotNull();
        assertThat(auditActions(manager)).contains("SUPPLY_CREATED");
    }

    @Test
    void AC_224_duplicateNamesInACategoryAndBadQuantitiesAreRefused() throws Exception {
        String name = unique("Oat");
        create(name, "FEED", 120);
        asJson(manager, post("/api/supplies"), item(name.toUpperCase(), "FEED", 5))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SUPPLY"));
        create(name, "TOOLS", 5);
        for (Object bad : new Object[]{-1, 1.5, "abc"}) {
            asJson(manager, post("/api/supplies"), item(unique("Cỏ khô"), "FEED", bad)).andExpect(status().isBadRequest());
        }
        asJson(manager, post("/api/supplies"), item(unique("Cỏ khô"), "FEED", -1))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.fields.openingQuantity").exists());
        Map<String, Object> noPrice = item(unique("Cỏ khô"), "FEED", 5);
        noPrice.remove("price");
        asJson(manager, post("/api/supplies"), noPrice)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.price").exists());
        Map<String, Object> negativePrice = item(unique("Cỏ khô"), "FEED", 5);
        negativePrice.put("price", -1);
        asJson(manager, post("/api/supplies"), negativePrice)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.price").exists());
        asJson(manager, post("/api/supplies"), item(" ", "NOPE", 1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.category").exists());
    }

    @Test
    void AC_225_changingQuantityOrDeactivatingIsAuditedAndADeactivatedItemTakesNoNewProposal() throws Exception {
        JsonNode created = create(unique("Thuốc sát trùng"), "MEDICINE", 10);
        long id = created.get("id").asLong();
        long before = propose(groom, id, 20).get("id").asLong();

        // The quantity is never edited, only moved by receipts and adjustments (BR-171, AC-225).
        Map<String, Object> change = item(created.get("name").asText(), "MEDICINE", 40);
        Map<String, Object> withQuantity = new HashMap<>(change);
        withQuantity.put("quantity", 40);
        asJson(manager, put("/api/supplies/" + id), withQuantity)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUANTITY_BY_MOVEMENT"));
        assertThat(itemById(manager, id).get("quantity").asLong()).isEqualTo(10);
        change.put("unitId", unitId("ml"));
        assertThat(body(asJson(manager, put("/api/supplies/" + id), change).andExpect(status().isOk())).get("unit").asText())
                .isEqualTo("ml");
        change.put("active", false);
        assertThat(body(asJson(manager, put("/api/supplies/" + id), change).andExpect(status().isOk())).get("active").asBoolean()).isFalse();
        assertThat(auditActions(manager)).contains("SUPPLY_UPDATED");

        asJson(groom, post("/api/supplies/" + id + "/proposals"), Map.of("quantity", 5))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLY_INACTIVE"));
        assertThat(itemById(manager, id).get("active").asBoolean()).isFalse();
        assertThat(proposalById(manager, before)).isNotNull();
    }

    @Test
    void AC_226_aProposalIsPendingAndTheClubManagerIsNotified() throws Exception {
        JsonNode created = create(unique("Yến mạch"), "FEED", 120);
        JsonNode p = propose(groom, created.get("id").asLong(), 200);
        assertThat(p.get("state").asText()).isEqualTo("PENDING");
        assertThat(p.get("quantity").asLong()).isEqualTo(200);
        assertThat(p.get("note").asText()).isEqualTo("sắp hết");
        assertThat(p.get("groomName").asText()).isEqualTo(groom.displayName());
        assertThat(hasNotification(manager, "\"" + created.get("name").asText() + "\"")).isTrue();
        assertThat(auditActions(groom)).contains("RESTOCK_PROPOSED");
    }

    @Test
    void AC_227_zeroNegativeOrFractionalQuantitiesAndInactiveItemsAreRefused() throws Exception {
        JsonNode created = create(unique("Yến mạch"), "FEED", 120);
        long id = created.get("id").asLong();
        for (Object bad : new Object[]{0, -3, 2.5}) {
            asJson(groom, post("/api/supplies/" + id + "/proposals"), Map.of("quantity", bad))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields.quantity").exists());
        }
        asJson(groom, post("/api/supplies/" + id + "/proposals"), Map.of("note", "x"))
                .andExpect(status().isBadRequest());
        Map<String, Object> off = item(created.get("name").asText(), "FEED", 120);
        off.put("active", false);
        asJson(manager, put("/api/supplies/" + id), off).andExpect(status().isOk());
        asJson(groom, post("/api/supplies/" + id + "/proposals"), Map.of("quantity", 10))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLY_INACTIVE"));
    }

    @Test
    void AC_228_approvingNotifiesTheGroomIsAuditedAndLeavesTheStockAlone() throws Exception {
        JsonNode created = create(unique("Yến mạch"), "FEED", 120);
        long id = created.get("id").asLong();
        long proposal = propose(groom, id, 200).get("id").asLong();
        JsonNode decided = body(as(manager, post("/api/restock-proposals/" + proposal + "/approve")).andExpect(status().isOk()));
        assertThat(decided.get("state").asText()).isEqualTo("APPROVED");
        assertThat(decided.get("decidedByName").asText()).isEqualTo(manager.displayName());
        assertThat(decided.get("decidedAt").isNull()).isFalse();
        assertThat(hasNotification(groom, "đã được duyệt")).isTrue();
        assertThat(auditActions(manager)).contains("RESTOCK_APPROVED");
        assertThat(itemById(manager, id).get("quantity").asLong()).isEqualTo(120);
    }

    @Test
    void AC_229_rejectingWithAReasonShowsTheReasonToTheGroomAndIsAudited() throws Exception {
        long id = create(unique("Yến mạch"), "FEED", 120).get("id").asLong();
        long proposal = propose(groom, id, 200).get("id").asLong();
        JsonNode decided = body(asJson(manager, post("/api/restock-proposals/" + proposal + "/reject"),
                Map.of("reason", "Còn đủ dùng")).andExpect(status().isOk()));
        assertThat(decided.get("state").asText()).isEqualTo("REJECTED");
        JsonNode mine = proposalById(groom, proposal);
        assertThat(mine.get("state").asText()).isEqualTo("REJECTED");
        assertThat(mine.get("decisionReason").asText()).isEqualTo("Còn đủ dùng");
        assertThat(hasNotification(groom, "Còn đủ dùng")).isTrue();
        assertThat(auditActions(manager)).contains("RESTOCK_REJECTED");
    }

    @Test
    void AC_230_rejectingWithoutAReasonAndDecidingTwiceAreRefused() throws Exception {
        long id = create(unique("Yến mạch"), "FEED", 120).get("id").asLong();
        long proposal = propose(groom, id, 200).get("id").asLong();
        asJson(manager, post("/api/restock-proposals/" + proposal + "/reject"), Map.of())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.reason").exists());
        asJson(manager, post("/api/restock-proposals/" + proposal + "/reject"), Map.of("reason", " "))
                .andExpect(status().isBadRequest());
        assertThat(proposalById(manager, proposal).get("state").asText()).isEqualTo("PENDING");

        as(manager, post("/api/restock-proposals/" + proposal + "/approve")).andExpect(status().isOk());
        as(manager, post("/api/restock-proposals/" + proposal + "/approve"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROPOSAL_DECIDED"));
        asJson(manager, post("/api/restock-proposals/" + proposal + "/reject"), Map.of("reason", "muộn"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROPOSAL_DECIDED"));
        assertThat(proposalById(manager, proposal).get("state").asText()).isEqualTo("APPROVED");
    }

    @Test
    void AC_231_aGroomCannotEditOrDecideAndSeesActiveItemsAndOwnProposalsOnly() throws Exception {
        JsonNode active = create(unique("Yến mạch"), "FEED", 120);
        JsonNode retired = create(unique("Cỏ khô"), "FEED", 50);
        Map<String, Object> off = item(retired.get("name").asText(), "FEED", 50);
        off.put("active", false);
        asJson(manager, put("/api/supplies/" + retired.get("id").asLong()), off).andExpect(status().isOk());
        AccountPrincipal otherGroom = account(Role.GROOM);
        long mine = propose(groom, active.get("id").asLong(), 10).get("id").asLong();
        long theirs = propose(otherGroom, active.get("id").asLong(), 20).get("id").asLong();

        asJson(groom, post("/api/supplies"), item(unique("X"), "FEED", 1)).andExpect(status().isForbidden());
        asJson(groom, put("/api/supplies/" + active.get("id").asLong()), item("X", "FEED", 1)).andExpect(status().isForbidden());
        as(groom, post("/api/restock-proposals/" + mine + "/approve")).andExpect(status().isForbidden());
        asJson(groom, post("/api/restock-proposals/" + mine + "/reject"), Map.of("reason", "x")).andExpect(status().isForbidden());

        assertThat(itemById(groom, active.get("id").asLong())).isNotNull();
        assertThat(itemById(groom, retired.get("id").asLong())).isNull();
        assertThat(itemById(manager, retired.get("id").asLong())).isNotNull();
        assertThat(proposalById(groom, mine)).isNotNull();
        assertThat(proposalById(groom, theirs)).isNull();
        assertThat(proposalById(manager, theirs)).isNotNull();
        assertThat(proposalById(manager, mine).get("state").asText()).isEqualTo("PENDING");
    }

    @Test
    void AC_232_trainerVeterinarianAndOwnerCannotOpenSuppliesOrProposals() throws Exception {
        long id = create(unique("Yến mạch"), "FEED", 120).get("id").asLong();
        for (AccountPrincipal who : List.of(trainer, vet, owner)) {
            as(who, get("/api/supplies")).andExpect(status().isForbidden());
            as(who, get("/api/restock-proposals")).andExpect(status().isForbidden());
            asJson(who, post("/api/supplies/" + id + "/proposals"), Map.of("quantity", 5)).andExpect(status().isForbidden());
            asJson(who, post("/api/supplies"), item(unique("X"), "FEED", 1)).andExpect(status().isForbidden());
        }
        asJson(manager, post("/api/supplies/" + id + "/proposals"), Map.of("quantity", 5)).andExpect(status().isForbidden());
    }
}
