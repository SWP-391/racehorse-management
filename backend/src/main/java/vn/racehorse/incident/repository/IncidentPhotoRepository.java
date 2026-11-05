package vn.racehorse.incident.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.incident.entity.IncidentPhoto;

import java.util.List;

public interface IncidentPhotoRepository extends JpaRepository<IncidentPhoto, Long> {

    @Query("select p.id from IncidentPhoto p where p.reportId = :reportId order by p.id")
    List<Long> idsOfReport(@Param("reportId") Long reportId);

    List<IncidentPhoto> findByReportIdOrderByIdAsc(Long reportId);
}
