package vn.racehorse.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/** Club-wide settings. The time zone drives display and every time-based rule (NFR-007). */
@ConfigurationProperties(prefix = "club")
public record ClubProperties(String timeZone) {

    public ClubProperties {
        if (timeZone == null || timeZone.isBlank()) {
            timeZone = "Asia/Ho_Chi_Minh";
        }
        ZoneId.of(timeZone);
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
