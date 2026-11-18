package vn.racehorse.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may write to whom (BR-228 to BR-230, BR-234): one row per clause and per refusal (TECH-241). */
class MessagingPolicyTest extends InboxTestBase {

    private List<Long> offered(AccountPrincipal who, Long horseId) throws Exception {
        var request = get("/api/inbox/recipients");
        if (horseId != null) {
            request.param("horseId", horseId.toString());
        }
        JsonNode recipients = body(as(who, request).andExpect(status().isOk())).get("recipients");
        List<Long> ids = new ArrayList<>();
        recipients.forEach(r -> ids.add(r.get("clubManagers").asBoolean() ? -1L : r.get("accountId").asLong()));
        return ids;
    }

    @Test
    void AC_489_theTemporaryTrainerIsTheRecipientAndTheMainTrainerIsNot() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal t1 = account(Role.HEAD_TRAINER);
        AccountPrincipal t2 = account(Role.HEAD_TRAINER);
        long horse = horseOf(owner, t1);
        lend(horse, t2, 3);

        start(owner, toPerson("Hỏi về ngựa", "Xin chào", t1, horse)).andExpect(status().isForbidden());
        start(owner, toPerson("Hỏi về ngựa", "Xin chào", t2, horse)).andExpect(status().isCreated());
        assertThat(offered(owner, horse)).contains(t2.id()).doesNotContain(t1.id());
    }

    @Test
    void AC_490_anOwnerWritesToAVeterinarianWhoExaminedTheHorse() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal vet = account(Role.VETERINARIAN);
        long horse = horseOf(owner, account(Role.HEAD_TRAINER));
        examine(horse, vet);

        start(owner, toPerson("Chân trước", "Ngựa đi hơi khập khiễng", vet, horse)).andExpect(status().isCreated());
        assertThat(unread(vet)).isEqualTo(1);
        assertThat(offered(owner, horse)).contains(vet.id());
    }

    @Test
    void AC_491_aVeterinarianWhoNeverExaminedTheHorseIsRefusedAndNotOffered() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal vet = account(Role.VETERINARIAN);
        AccountPrincipal other = account(Role.VETERINARIAN);
        long horse = horseOf(owner, account(Role.HEAD_TRAINER));
        examine(horse, other);

        start(owner, toPerson("Chân trước", "Xin chào", vet, horse)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_ALLOWED"));
        assertThat(offered(owner, horse)).doesNotContain(vet.id()).contains(other.id());
        assertThat(list(owner)).isEmpty();
    }

    @Test
    void AC_492_otherPairsAreRefused() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal trainer = account(Role.HEAD_TRAINER);
        long horse = horseOf(owner, trainer);

        start(owner, toPerson("a", "b", account(Role.GROOM), horse)).andExpect(status().isForbidden());
        start(owner, toPerson("a", "b", account(Role.GROOM), null)).andExpect(status().isForbidden());
        start(owner, toPerson("a", "b", account(Role.HORSE_OWNER), horse)).andExpect(status().isForbidden());
        // the Head Trainer of a horse that is not theirs
        start(owner, toPerson("a", "b", account(Role.HEAD_TRAINER), horse)).andExpect(status().isForbidden());
        // and a horse that belongs to another owner
        long strangers = horseOf(account(Role.HORSE_OWNER), trainer);
        start(owner, toPerson("a", "b", trainer, strangers)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.horseId").exists());
        assertThat(list(owner)).isEmpty();
    }

    @Test
    void AC_493_theHorseIsRequiredAndMustLinkThePeople() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal trainer = account(Role.HEAD_TRAINER);
        AccountPrincipal vet = account(Role.VETERINARIAN);
        long horse = horseOf(owner, trainer);
        examine(horse, vet);

        start(owner, toPerson("a", "b", trainer, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HORSE_REQUIRED")).andExpect(jsonPath("$.fields.horseId").exists());
        start(owner, toPerson("a", "b", vet, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HORSE_REQUIRED"));
        long strangers = horseOf(account(Role.HORSE_OWNER), trainer);
        start(owner, toPerson("a", "b", trainer, strangers)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HORSE_NOT_LINKED"));
        archive(horse);
        start(owner, toPerson("a", "b", trainer, horse)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HORSE_ARCHIVED"));
        assertThat(list(owner)).isEmpty();
    }

    @Test
    void AC_494_aVeterinarianAndTheResponsibleTrainerWriteToEachOther() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal trainer = account(Role.HEAD_TRAINER);
        AccountPrincipal vet = account(Role.VETERINARIAN);
        long horse = horseOf(owner, trainer);

        long id = started(vet, toPerson("Chấn thương", "Ngựa chuyển sang Chấn thương", trainer, horse));
        send(trainer, id, "Đã nhận, tôi hoãn lịch tập").andExpect(status().isCreated());
        assertThat(unread(vet)).isEqualTo(1);
        // the other direction, for another horse of the trainer
        long other = horseOf(account(Role.HORSE_OWNER), trainer);
        started(trainer, toPerson("Hỏi thú y", "Cần khám lại", vet, other));
        // a trainer who is not responsible for the horse is refused
        AccountPrincipal stranger = account(Role.HEAD_TRAINER);
        start(stranger, toPerson("a", "b", vet, horse)).andExpect(status().isForbidden());
        start(vet, toPerson("a", "b", stranger, horse)).andExpect(status().isForbidden());
    }

    @Test
    void AC_495_aGroomAndAnyHeadTrainerWriteToEachOtherWithoutAHorse() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);
        AccountPrincipal trainer = account(Role.HEAD_TRAINER);

        long id = started(groom, toPerson("Dọn chuồng", "Hôm nay dọn chuồng mấy giờ?", trainer, null));
        send(trainer, id, "Sau buổi tập sáng").andExpect(status().isCreated());
        started(trainer, toPerson("Lịch", "Mai làm sớm", groom, null));
        assertThat(offered(groom, null)).contains(trainer.id());
        assertThat(offered(trainer, null)).contains(groom.id());
    }

    @Test
    void theOwnerAndTheirTrainerOrVeterinarianCanAnswerBack() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        AccountPrincipal trainer = account(Role.HEAD_TRAINER);
        AccountPrincipal vet = account(Role.VETERINARIAN);
        long horse = horseOf(owner, trainer);
        examine(horse, vet);

        started(trainer, toPerson("Lịch tập", "Tuần này nghỉ", owner, horse));
        started(vet, toPerson("Kết quả khám", "Ổn", owner, horse));
        assertThat(unread(owner)).isEqualTo(2);
        // a Veterinarian cannot reach an owner whose horse they never examined
        AccountPrincipal stranger = account(Role.HORSE_OWNER);
        long other = horseOf(stranger, trainer);
        start(vet, toPerson("a", "b", stranger, other)).andExpect(status().isForbidden());
        // and a horse that is not the owner's is refused on the horse
        start(vet, toPerson("a", "b", account(Role.HORSE_OWNER), other)).andExpect(status().isBadRequest());
    }

    @Test
    void AC_497_theClubManagerWritesToAnyActiveAccountAndEveryRoleToTheClubManagers() throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        AccountPrincipal gone = account(Role.GROOM);
        for (Role role : Role.values()) {
            if (role == Role.CLUB_MANAGER) {
                continue;
            }
            AccountPrincipal who = account(role);
            started(who, toManagers("Góp ý", "Xin chào"));
            started(manager, toPerson("Gửi " + role, "Xin chào", who, null));
        }
        jdbc.update("UPDATE user_account SET active = false WHERE id = ?", gone.id());
        start(manager, toPerson("a", "b", gone, null)).andExpect(status().isForbidden());
        assertThat(offered(manager, null)).doesNotContain(gone.id());
        // the Club Manager role is not a recipient of a Club Manager
        start(manager, toManagers("a", "b")).andExpect(status().isForbidden());
        assertThat(offered(manager, null)).doesNotContain(-1L);
        assertThat(offered(account(Role.HORSE_OWNER), null)).contains(-1L);
    }

    @Test
    void AC_503_nobodyWritesToThemself() throws Exception {
        for (Role role : Role.values()) {
            AccountPrincipal who = account(role);
            long before = jdbc.queryForObject("SELECT count(*) FROM conversation", Long.class);
            start(who, toPerson("a", "b", who, null)).andExpect(status().isForbidden());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation", Long.class)).isEqualTo(before);
            assertThat(offered(who, null)).doesNotContain(who.id());
        }
    }

    @Test
    void everyOtherPairIsRefused() throws Exception {
        AccountPrincipal groom = account(Role.GROOM);
        AccountPrincipal vet = account(Role.VETERINARIAN);
        AccountPrincipal trainer = account(Role.HEAD_TRAINER);
        start(groom, toPerson("a", "b", vet, null)).andExpect(status().isForbidden());
        start(vet, toPerson("a", "b", groom, null)).andExpect(status().isForbidden());
        start(trainer, toPerson("a", "b", account(Role.HEAD_TRAINER), null)).andExpect(status().isForbidden());
        start(groom, toPerson("a", "b", account(Role.GROOM), null)).andExpect(status().isForbidden());
        start(vet, toPerson("a", "b", account(Role.VETERINARIAN), null)).andExpect(status().isForbidden());
        start(account(Role.HORSE_OWNER), toPerson("a", "b", account(Role.HORSE_OWNER), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void theRecipientIsExactlyOneOfAPersonOrTheClubManagers() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        var neither = toManagers("a", "b");
        neither.remove("toClubManagers");
        start(owner, neither).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.recipient").exists());
        var both = toManagers("a", "b");
        both.put("recipientId", account(Role.CLUB_MANAGER).id());
        start(owner, both).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.recipient").exists());
    }

    @Test
    void anOwnerCannotNameAHorseOfAnotherOwnerEvenForTheClubManagers() throws Exception {
        AccountPrincipal owner = account(Role.HORSE_OWNER);
        long strangers = horseOf(account(Role.HORSE_OWNER), account(Role.HEAD_TRAINER));
        var body = toManagers("a", "b");
        body.put("horseId", strangers);
        start(owner, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HORSE_NOT_LINKED"));
        var own = toManagers("a", "b");
        own.put("horseId", horseOf(owner, account(Role.HEAD_TRAINER)));
        start(owner, own).andExpect(status().isCreated());
    }
}
