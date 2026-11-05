package vn.racehorse.incident.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.incident.entity.IncidentReport;

import java.util.List;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

    List<IncidentReport> findByGroomIdOrderByCreatedAtDescIdDesc(Long groomId);

    List<IncidentReport> findAllByOrderByCreatedAtDescIdDesc();
}
