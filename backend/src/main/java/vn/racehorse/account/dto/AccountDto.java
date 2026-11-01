package vn.racehorse.account.dto;

import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;

import java.time.Instant;

/** {@code hasSchedule} is set only in the list and only for a Groom: false flags "Chưa có lịch làm" (BR-162). */
public record AccountDto(Long id, String username, String displayName, Role role, boolean active,
                         Instant createdAt,
                         @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                         Boolean hasSchedule) {
    public static AccountDto of(UserAccount a) {
        return of(a, null);
    }

    public static AccountDto of(UserAccount a, Boolean hasSchedule) {
        return new AccountDto(a.getId(), a.getUsername(), a.getDisplayName(), a.getRole(), a.isActive(),
                a.getCreatedAt(), hasSchedule);
    }
}
