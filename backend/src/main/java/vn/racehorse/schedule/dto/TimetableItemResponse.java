package vn.racehorse.schedule.dto;

import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.service.TrainingEnums.SessionState;
import vn.racehorse.training.service.TrainingEnums.SessionType;

import java.time.LocalTime;

/** One session in a timetable; `startTime` is null for a session created without a planned time (BR-200). */
public record TimetableItemResponse(Long id, Long horseId, String horseName, LocalTime startTime, String plannedEnd,
                                    boolean endsNextDay, SessionType type, SessionState state, String groomName) {
    public static TimetableItemResponse of(TrainingSession s, String horseName, boolean withGroom) {
        LocalTime start = s.getPlannedStartTime();
        int startMin = start == null ? 0 : start.toSecondOfDay() / 60;
        int end = startMin + s.getPlannedDurationMin();
        String plannedEnd = start == null ? null : start.plusMinutes(s.getPlannedDurationMin()).toString();
        String groom = withGroom && s.getResponsiblePerson() != null ? s.getResponsiblePerson().getDisplayName() : null;
        return new TimetableItemResponse(s.getId(), s.getHorseId(), horseName, start, plannedEnd,
                start != null && end >= 24 * 60, s.getType(), s.getState(), groom);
    }
}
