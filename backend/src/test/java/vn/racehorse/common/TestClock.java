package vn.racehorse.common;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import vn.racehorse.common.config.ClubProperties;

/** A system clock that tests can shift (for "yesterday" scenarios). Always reset after each test. */
public final class TestClock extends Clock {

    private static volatile Duration offset = Duration.ZERO;

    public static void shift(Duration by) {
        offset = by;
    }

    public static void reset() {
        offset = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset);
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        ClubClock testClubClock(ClubProperties club) {
            return new ClubClock(club, new TestClock());
        }
    }
}
