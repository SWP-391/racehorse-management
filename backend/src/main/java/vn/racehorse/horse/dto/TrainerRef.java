package vn.racehorse.horse.dto;

import vn.racehorse.account.entity.UserAccount;

/** The Head Trainer who writes now, the main one, and the temporary one with the return date (BR-152, BR-156). */
public record TrainerRef(Long id, String displayName, boolean active) {
    public static TrainerRef of(UserAccount a) {
        return a == null ? null : new TrainerRef(a.getId(), a.getDisplayName(), a.isActive());
    }
}
