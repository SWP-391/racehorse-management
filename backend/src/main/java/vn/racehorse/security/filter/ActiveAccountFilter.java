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
import vn.racehorse.security.LoginSessionService;

import java.io.IOException;
import java.util.Optional;

/**
 * Re-checks the signed-in account on every request. A deactivated account loses its open sessions at once
 * (BR-090, TECH-006); a role change or a rename takes effect on the next request (BR-239).
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    private final UserAccountRepository accounts;

    private final LoginSessionService loginSessions;

    public ActiveAccountFilter(UserAccountRepository accounts, LoginSessionService loginSessions) {
        this.accounts = accounts;
        this.loginSessions = loginSessions;
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
            if (current.getPasswordVersion() != principal.passwordVersion()) {
                // The password was reset after this session opened: it ends here (BR-216, TECH-223).
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Vui lòng đăng nhập.\",\"fields\":{}}");
                return;
            }
            HttpSession live = request.getSession(false);
            if (live != null && !loginSessions.useOrEnd(current.getId(), request, live)) {
                // This sign-in was ended from another session of the same person (BR-254).
                SecurityContextHolder.clearContext();
                live.invalidate();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Vui lòng đăng nhập.\",\"fields\":{}}");
                return;
            }
            if (current.getRole() != principal.role() || !current.getDisplayName().equals(principal.displayName())) {
                AccountPrincipal refreshed = AccountPrincipal.of(current);
                var updated = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        refreshed, null, refreshed.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(updated);
            }
        }
        chain.doFilter(request, response);
    }
}
