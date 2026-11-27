package vn.racehorse.account.dto;

import vn.racehorse.security.LoginSessionService;

import java.time.Instant;

/** One sign-in of the person: the browser's own description, when it opened, when it was last used (BR-254). */
public record LoginSessionDto(long id, String userAgent, Instant createdAt, Instant lastUsedAt, boolean current) {

    public static LoginSessionDto of(LoginSessionService.Row row) {
        return new LoginSessionDto(row.id(), row.userAgent(), row.createdAt(), row.lastUsedAt(), row.current());
    }
}
