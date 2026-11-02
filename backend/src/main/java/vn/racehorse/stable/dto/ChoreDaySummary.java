package vn.racehorse.stable.dto;

import java.time.LocalDate;

/** One day of the chore history: horses listed, chores they had and how many were done (BR-188, BR-209). */
public record ChoreDaySummary(LocalDate date, int horses, int chores, int done) {
}
