package vn.racehorse.inbox.dto;

import vn.racehorse.account.entity.Role;

/** Someone the caller may write to: a person, or the Club Manager role ({@code accountId} null, {@code clubManagers} true). */
public record Recipient(Long accountId, String name, Role role, boolean clubManagers) {
}
