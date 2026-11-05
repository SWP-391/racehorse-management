package vn.racehorse.incident.dto;

import vn.racehorse.horse.entity.Horse;
import vn.racehorse.incident.entity.IncidentReport;

import java.util.List;

public record IncidentDetail(IncidentReport report, Horse horse, List<Long> photoIds) {
}
