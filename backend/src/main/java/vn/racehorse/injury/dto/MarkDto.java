package vn.racehorse.injury.dto;

import vn.racehorse.injury.entity.InjuryMark;
import vn.racehorse.injury.entity.MarkStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MarkDto(Long id, Long horseId, String regionCode, MarkStatus status, Long examinationId,
                      LocalDate examinationDate, String examinationDiagnosis, String createdBy, Instant createdAt,
                      List<EventDto> history) {
    public static MarkDto of(InjuryMarkView v) {
        InjuryMark m = v.mark();
        return new MarkDto(m.getId(), m.getHorseId(), m.getRegion().name(), m.getStatus(), m.getExaminationId(),
                v.examination() == null ? null : v.examination().getExamDate(),
                v.examination() == null ? null : v.examination().getDiagnosis(),
                m.getCreatedBy().getDisplayName(), m.getCreatedAt(),
                v.history().stream().map(e -> new EventDto(e.getFrom(), e.getTo(), e.getEventDate(), e.getNote(),
                        e.getBy().getDisplayName(), e.getAt())).toList());
    }
}
