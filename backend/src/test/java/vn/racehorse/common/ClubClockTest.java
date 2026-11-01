package vn.racehorse.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import vn.racehorse.common.config.ClubProperties;

class ClubClockTest {

    @Test
    void defaultsToVietnamTime() {
        assertThat(new ClubProperties(null).zone().getId()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void convertsUtcToClubTime() {
        ClubClock clock = new ClubClock(new ClubProperties("Asia/Ho_Chi_Minh"));
        var local = clock.toClubTime(Instant.parse("2026-10-21T17:30:00Z"));
        assertThat(local.toLocalDate()).hasToString("2026-10-22");
        assertThat(local.toLocalTime()).hasToString("00:30");
    }

    @Test
    void rejectsUnknownZone() {
        assertThatThrownBy(() -> new ClubProperties("Mars/Olympus")).isInstanceOf(RuntimeException.class);
    }
}
