package vn.racehorse.schedule.dto;

import vn.racehorse.schedule.entity.GroomLeave;

import java.time.Instant;
import java.time.LocalDate;

public record LeaveDto(Long id, LocalDate date, boolean late, int noticeDays, String reason, String recordedBy,
                       Instant recordedAt, boolean cancelled) {
    public static LeaveDto of(GroomLeave l) {
        return new LeaveDto(l.getId(), l.getLeaveDate(), l.isLate(), l.getNoticeDays(), l.getReason(),
                l.getRecordedBy().getDisplayName(), l.getRecordedAt(), l.isCancelled());
    }
}
