package vn.racehorse.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.service.HorseTrainerService;

import java.util.Arrays;
import java.util.List;

/** Server-side permission checks used by every endpoint (BR-091, TECH-007). */
@Component
public class AccessGuard {

    /** Looked up lazily: the trainer service writes notices, and notices need this guard (TECH-130). */
    private final ObjectProvider<HorseTrainerService> trainers;

    public AccessGuard(ObjectProvider<HorseTrainerService> trainers) {
        this.trainers = trainers;
    }

    public AccountPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
        }
        return principal;
    }

    /** Returns the current user if their role holds the permission; refuses otherwise. */
    public AccountPrincipal require(Permission permission) {
        AccountPrincipal user = current();
        if (!permission.allows(user.role())) {
            throw ApiException.forbidden();
        }
        return user;
    }

    /**
     * The permission, and then: the caller is the assigned Head Trainer of the horse (BR-153). Reads are never
     * guarded this way, every Head Trainer sees every horse.
     */
    public AccountPrincipal requireTrainerOf(Permission permission, Long horseId) {
        AccountPrincipal user = require(permission);
        trainers.getObject().requireWriter(user, horseId);
        return user;
    }

    /** Returns the current user if their role holds at least one of the permissions; refuses otherwise. */
    public AccountPrincipal requireAny(Permission... permissions) {
        AccountPrincipal user = current();
        if (Arrays.stream(permissions).noneMatch(p -> p.allows(user.role()))) {
            throw ApiException.forbidden();
        }
        return user;
    }

    public List<String> permissionsOf(AccountPrincipal user) {
        return Arrays.stream(Permission.values())
                .filter(p -> p.allows(user.role()))
                .map(Enum::name)
                .toList();
    }
}
