package vn.racehorse.training.dto;

import vn.racehorse.account.entity.UserAccount;

public record PersonRef(Long id, String displayName) {
    public static PersonRef of(UserAccount a) {
        return a == null ? null : new PersonRef(a.getId(), a.getDisplayName());
    }
}
