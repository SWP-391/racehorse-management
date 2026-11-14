package vn.racehorse.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.racehorse.account.EmailAddress;
import vn.racehorse.account.repository.UserAccountRepository;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository accounts;

    public AccountUserDetailsService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        // The "username" of Spring Security is the email address (BR-210); an input that is no email is just unknown.
        EmailAddress email = EmailAddress.tryParse(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return accounts.findByEmail(email.value())
                .map(AccountPrincipal::of)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
