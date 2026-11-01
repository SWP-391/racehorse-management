package vn.racehorse.schedule.dto;

import vn.racehorse.common.validation.ClubDate;

import java.time.LocalDate;

public record LeaveRequest(@ClubDate LocalDate date, String reason) {
}
