package vn.racehorse.incident.dto;

import vn.racehorse.incident.entity.IncidentReport;
import vn.racehorse.incident.entity.IncidentState;

import java.time.Instant;
import java.util.List;

public record IncidentDto(Long id, Long horseId, String horseName, String groom, String description,
                          String bodyRegion, IncidentState state, Instant createdAt, Instant acknowledgedAt,
                          String closingNote, Long linkedExaminationId, Instant closedAt, List<Long> photoIds) {
    public static IncidentDto of(IncidentDetail d) {
        IncidentReport r = d.report();
        return new IncidentDto(r.getId(), r.getHorseId(), d.horse().getName(), r.getGroom().getDisplayName(),
                r.getDescription(), r.getBodyRegion() == null ? null : r.getBodyRegion().name(), r.getState(),
                r.getCreatedAt(), r.getAcknowledgedAt(), r.getClosingNote(), r.getLinkedExaminationId(),
                r.getClosedAt(), d.photoIds());
    }
}
