package vn.racehorse.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.Permission;

/**
 * Account administration is checked before the request body is read, so a caller without the permission always gets
 * the same refusal and never a validation message that describes the endpoint (TECH-125, BR-091). The services keep
 * their own check.
 */
@Configuration
class AccountsAccessConfig implements WebMvcConfigurer {

    private final AccessGuard access;

    AccountsAccessConfig(AccessGuard access) {
        this.access = access;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response, Object handler) {
                access.require(Permission.MANAGE_ACCOUNTS);
                return true;
            }
        }).addPathPatterns("/api/accounts", "/api/accounts/**")
                // The schedule and leave of a Groom have their own permissions (PERM-047, PERM-048), checked below.
                .excludePathPatterns("/api/accounts/*/schedule", "/api/accounts/*/leave");
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response, Object handler) {
                access.require("GET".equals(request.getMethod()) ? Permission.VIEW_SCHEDULES : Permission.MANAGE_SCHEDULES);
                return true;
            }
        }).addPathPatterns("/api/accounts/*/schedule", "/api/accounts/*/leave");
    }
}
