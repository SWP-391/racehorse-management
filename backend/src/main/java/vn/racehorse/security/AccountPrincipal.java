package vn.racehorse.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;

import java.util.Collection;
import java.util.List;

/** The signed-in user, kept in the HTTP session. */
public record AccountPrincipal(Long id, String email, String displayName, String passwordHash, Role role,
                               boolean active, long passwordVersion) implements UserDetails {

    public static AccountPrincipal of(UserAccount account) {
        return new AccountPrincipal(account.getId(), account.getEmail(), account.getDisplayName(),
                account.getPasswordHash(), account.getRole(), account.isActive(), account.getPasswordVersion());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    /** Never expose the hash when the principal is serialised. */
    @Override
    public String toString() {
        return "AccountPrincipal[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
