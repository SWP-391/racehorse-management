package vn.racehorse.account.dto;

import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;

/** The own profile of a signed-in person (BR-254). */
public record ProfileDto(String email, String displayName, String phone, Role role) {

    public static ProfileDto of(UserAccount account) {
        return new ProfileDto(account.getEmail(), account.getDisplayName(), account.getPhone(), account.getRole());
    }
}
