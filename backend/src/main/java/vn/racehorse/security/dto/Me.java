package vn.racehorse.security.dto;

import vn.racehorse.account.entity.Role;

import java.util.List;

public record Me(Long id, String username, String displayName, Role role, List<String> permissions) {
}
