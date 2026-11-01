package vn.racehorse.security.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.deploy.config.FrontendConfig;
import vn.racehorse.security.AccountUserDetailsService;
import vn.racehorse.security.filter.ActiveAccountFilter;

import java.io.IOException;

/**
 * Session-cookie authentication with CSRF protection for a single-page client (TECH-006).
 * Authorization is checked per endpoint through {@link AccessGuard} (TECH-007).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, UserAccountRepository accounts,
                                    ObjectProvider<FrontendConfig> frontend) throws Exception {
        boolean clientBundled = frontend.getIfAvailable() != null;
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(SecurityConfig::refuseUnauthenticated)
                        .accessDeniedHandler(SecurityConfig::refuseForbidden))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/api/auth/login", "/api/auth/csrf", "/api/openapi/**",
                                    "/api/swagger-ui", "/api/swagger-ui/**")
                            .permitAll();
                    if (clientBundled) {
                        // Web client files and client-side routes (TECH-108); the API below stays protected.
                        auth.requestMatchers(SecurityConfig::isClientFileRequest).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterAfter(new ActiveAccountFilter(accounts), SecurityContextHolderFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), SecurityContextHolderFilter.class);
        return http.build();
    }

    /**
     * A request with no signed-in person is refused with the usual JSON error, never an empty answer, so the client
     * can tell an ended session from any other failure (BR-191, TECH-128).
     */
    private static void refuseUnauthenticated(HttpServletRequest request, HttpServletResponse response,
                                              AuthenticationException exception) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Vui lòng đăng nhập.\",\"fields\":{}}");
    }

    /** A refusal by the security filters themselves (a missing or wrong CSRF token) has the usual JSON error too (TECH-190). */
    private static void refuseForbidden(HttpServletRequest request, HttpServletResponse response,
                                        org.springframework.security.access.AccessDeniedException exception) throws IOException {
        vn.racehorse.common.exception.ApiException denied = vn.racehorse.common.exception.ApiException.forbidden();
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"" + denied.code() + "\",\"message\":\"" + denied.getMessage() + "\",\"fields\":{}}");
    }

    /** Anonymous GET or HEAD for anything outside the API and the actuator, only used when a client is bundled. */
    static boolean isClientFileRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            return false;
        }
        String path = request.getRequestURI();
        return !(path.equals("/api") || path.startsWith("/api/") || path.equals("/actuator") || path.startsWith("/actuator/"));
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AccountUserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    /** Forces the deferred CSRF token to load so the XSRF-TOKEN cookie is always sent to the client. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
