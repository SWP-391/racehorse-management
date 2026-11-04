package vn.racehorse.horse;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;

import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Horse registration, uniqueness, mandatory fields and owner visibility (REQ-022, REQ-030). */
class HorseRegistryTest extends ApiTest {

    private static java.util.List<Long> ids(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(n -> n.get("id").asLong()).toList();
    }

    @Test
    void AC_001_horseRegisteredAndVisibleToItsOwner() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        JsonNode horse = body(asJson(manager, post("/api/horses"),
                horseBody("Sao Mai", "FEMALE", owner.id()))
                .andExpect(status().isCreated()));
        assertThat(horse.get("name").asText()).isEqualTo("Sao Mai");
        assertThat(horse.get("owner").get("id").asLong()).isEqualTo(owner.id());
        assertThat(horse.get("archived").asBoolean()).isFalse();

        assertThat(ids(body(as(owner, get("/api/horses")).andExpect(status().isOk()))))
                .containsExactly(horse.get("id").asLong());
    }

    private JsonNode register(AccountPrincipal manager, AccountPrincipal owner) throws Exception {
        return body(asJson(manager, post("/api/horses"), horseBody(unique("Horse"), "MALE", owner.id()))
                .andExpect(status().isCreated()));
    }

    private static long suffix(JsonNode horse) {
        String number = horse.get("registrationNumber").asText();
        return Long.parseLong(number.substring(number.lastIndexOf('-') + 1));
    }

    @Test
    void AC_382_generatedNumberHasTheYearAndAtLeastFourDigits() throws Exception {
        JsonNode horse = register(account(Role.CLUB_MANAGER), account(Role.HORSE_OWNER));
        assertThat(horse.get("registrationNumber").asText())
                .matches("NH-" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).getYear() + "-\\d{4,}");
    }

    @Test
    void AC_383_nextNumberFollowsTheLastOne() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long first = suffix(register(manager, owner));
        assertThat(suffix(register(manager, owner))).isEqualTo(first + 1);
    }

    @Test
    void AC_384_simultaneousRegistrationsGetDifferentNumbers() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> register(manager, owner).get("registrationNumber").asText()));
            }
            java.util.Set<String> numbers = new java.util.HashSet<>();
            for (var f : futures) {
                numbers.add(f.get());
            }
            assertThat(numbers).hasSize(8);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void AC_385_numberOfAnArchivedHorseIsNotReused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        JsonNode first = register(manager, owner);
        asJson(manager, post("/api/horses/" + first.get("id").asLong() + "/archive"), Map.of("reason", "SOLD"))
                .andExpect(status().isOk());
        assertThat(register(manager, owner).get("registrationNumber").asText())
                .isNotEqualTo(first.get("registrationNumber").asText());
    }

    @Test
    void AC_386_aTypedNumberIsRefusedAndTheStoredOneIsKept() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        Map<String, Object> typed = horseBody("Tự gõ", "MALE", owner.id());
        typed.put("registrationNumber", "TYPED-1");
        asJson(manager, post("/api/horses"), typed).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.registrationNumber").exists());

        JsonNode horse = register(manager, owner);
        long id = horse.get("id").asLong();
        asJson(manager, put("/api/horses/" + id), typed).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.registrationNumber").exists());
        assertThat(body(as(manager, get("/api/horses/" + id))).get("registrationNumber").asText())
                .isEqualTo(horse.get("registrationNumber").asText());
    }

    @Test
    void editingAHorseKeepsItsNumber() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        JsonNode horse = register(manager, owner);
        long id = horse.get("id").asLong();
        asJson(manager, put("/api/horses/" + id), horseBody("Tên mới", "MALE", owner.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value(horse.get("registrationNumber").asText()));
    }

    @Test
    void AC_387_onlyTheClubManagerRegisters() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        for (Role role : new Role[] {Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER}) {
            asJson(account(role), post("/api/horses"), horseBody("X", "MALE", owner.id()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void AC_004_missingBirthDateRefusedAndFieldNamed() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        Map<String, Object> body = horseBody("Thiếu Ngày", "MALE", owner.id());
        body.remove("birthDate");
        asJson(manager, post("/api/horses"), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.birthDate").value("Bắt buộc nhập."));
    }

    @Test
    void AC_005_headTrainerCannotRegisterOrEdit() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal trainer = account(Role.HEAD_TRAINER);
        asJson(trainer, post("/api/horses"), horseBody("X", "MALE", owner.id()))
                .andExpect(status().isForbidden());
        long id = registerHorse(manager, owner, "MALE");
        asJson(trainer, put("/api/horses/" + id), horseBody("Đổi tên", "MALE", owner.id()))
                .andExpect(status().isForbidden());
    }

    @Test
    void AC_006_profileEditAppearsInAuditLogWithWhoAndWhen() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long id = registerHorse(manager, owner, "MALE");
        JsonNode horse = body(as(manager, get("/api/horses/" + id)));
        Map<String, Object> edit = horseBody("Tên mới", "MALE", owner.id());
        asJson(manager, put("/api/horses/" + id), edit).andExpect(status().isOk());

        JsonNode log = body(as(manager, get("/api/audit").param("actorId", manager.id().toString())));
        JsonNode entry = StreamSupport.stream(log.spliterator(), false)
                .filter(e -> e.get("action").asText().equals("HORSE_UPDATED") && e.get("targetId").asLong() == id)
                .findFirst().orElseThrow();
        assertThat(entry.get("actorName").asText()).isEqualTo(manager.displayName());
        assertThat(entry.get("occurredAtClub").asText()).isNotBlank();
        assertThat(json.readTree(entry.get("details").asText()).get("name").get("to").asText()).isEqualTo("Tên mới");
    }

    @Test
    void AC_017_ownerListsOwnHorsesWithProfileAndPedigree() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long a = registerHorse(manager, owner, "MALE");
        long b = registerHorse(manager, owner, "FEMALE");
        registerHorse(manager, account(Role.HORSE_OWNER), "MALE");

        assertThat(ids(body(as(owner, get("/api/horses"))))).containsExactlyInAnyOrder(a, b);
        as(owner, get("/api/horses/" + a)).andExpect(status().isOk()).andExpect(jsonPath("$.birthDate").exists());
        as(owner, get("/api/horses/" + a + "/pedigree")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void AC_018_anotherOwnersHorseRefused() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long other = registerHorse(manager, account(Role.HORSE_OWNER), "MALE");
        as(owner, get("/api/horses/" + other)).andExpect(status().isForbidden());
        as(owner, get("/api/horses/" + other + "/pedigree")).andExpect(status().isForbidden());
    }

    @Test
    void AC_019_ownerChangeSwitchesVisibility() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal o1 = account(Role.HORSE_OWNER);
        AccountPrincipal o2 = account(Role.HORSE_OWNER);
        long id = registerHorse(manager, o1, "MALE");
        JsonNode horse = body(as(manager, get("/api/horses/" + id)));

        asJson(manager, put("/api/horses/" + id),
                horseBody(horse.get("name").asText(), "MALE", o2.id()))
                .andExpect(status().isOk());

        assertThat(ids(body(as(o1, get("/api/horses"))))).doesNotContain(id);
        assertThat(ids(body(as(o2, get("/api/horses"))))).contains(id);
        as(o1, get("/api/horses/" + id)).andExpect(status().isForbidden());
    }

    @Test
    void groomHasNoHorseProfileAccess() throws Exception {
        as(account(Role.GROOM), get("/api/horses")).andExpect(status().isForbidden());
    }

    @Test
    void ownerMustBeAnActiveHorseOwnerAccount() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        asJson(manager, post("/api/horses"), horseBody("X", "MALE", account(Role.GROOM).id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.ownerId").exists());
    }
}
