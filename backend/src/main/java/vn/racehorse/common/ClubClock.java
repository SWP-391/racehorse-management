package vn.racehorse.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.racehorse.common.config.ClubProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Current time and conversions to club local time. Times are stored in UTC. */
@Component
public class ClubClock {

    private final ClubProperties club;
    private final Clock clock;

    @Autowired
    public ClubClock(ClubProperties club) {
        this(club, Clock.systemUTC());
    }

    ClubClock(ClubProperties club, Clock clock) {
        this.club = club;
        this.clock = clock;
    }

    public java.time.ZoneId zone() {
        return club.zone();
    }

    public Instant now() {
        return clock.instant();
    }

    /** The current club-local date. */
    public LocalDate today() {
        return toClubTime(now()).toLocalDate();
    }

    public ZonedDateTime toClubTime(Instant instant) {
        return instant.atZone(club.zone());
    }
}
