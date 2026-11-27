package vn.racehorse.horse.dto;

import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.horse.entity.CareRole;
import vn.racehorse.horse.entity.Horse;

import java.time.LocalDate;

/**
 * The care team of a horse: main people and the substitutes that are still in date (BR-256 to BR-259). A null member
 * is "Chưa gán". The shape is the `CareTeam` of frontend/src/api/types.ts.
 */
public record CareTeam(Member headTrainer, Member veterinarian, Member groom,
                       Substitute temporaryVeterinarian, Substitute temporaryGroom) {

    public record Member(Long id, String displayName) {
        static Member of(UserAccount a) {
            return a == null ? null : new Member(a.getId(), a.getDisplayName());
        }
    }

    public record Substitute(Long id, String displayName, LocalDate until) {
        static Substitute of(UserAccount a, LocalDate until) {
            return a == null || until == null ? null : new Substitute(a.getId(), a.getDisplayName(), until);
        }
    }

    public static CareTeam of(Horse h) {
        return new CareTeam(Member.of(h.getMainTrainer()), Member.of(h.getVet()), Member.of(h.getGroom()),
                Substitute.of(h.getTempVet(), h.getTempVetUntil()), Substitute.of(h.getTempGroom(), h.getTempGroomUntil()));
    }

    public Member of(CareRole role) {
        return switch (role) {
            case HEAD_TRAINER -> headTrainer;
            case VETERINARIAN -> veterinarian;
            case GROOM -> groom;
        };
    }
}
