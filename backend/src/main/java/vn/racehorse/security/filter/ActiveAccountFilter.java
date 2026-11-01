package vn.racehorse.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.security.AccountPrincipal;

import java.io.IOException;
import java.util.Optional;

/**
 * Re-checks the signed-in account on every request. A deactivated account loses its open sessions at once
 * (BR-090, TECH-006); a role change takes effect on the next request.
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    private final UserAccountRepository accounts;

    public ActiveAccountFilter(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
            Optional<UserAccount> account = accounts.findById(principal.id());
            if (account.isEmpty() || !account.get().isActive()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                        "{\"code\":\"ACCOUNT_DEACTIVATED\",\"message\":\"Tài khoản đã bị vô hiệu hóa.\",\"fields\":{}}");
                return;
            }
            UserAccount current = account.get();
            if (current.getRole() != principal.role()) {
                AccountPrincipal refreshed = AccountPrincipal.of(current);
                var updated = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        refreshed, null, refreshed.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(updated);
            }
        }
        chain.doFilter(request, response);
    }
}
