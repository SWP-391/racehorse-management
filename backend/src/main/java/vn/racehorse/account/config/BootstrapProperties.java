package vn.racehorse.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credentials of the first Club Manager (TECH-006). */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String username, String password, String displayName) {
}
