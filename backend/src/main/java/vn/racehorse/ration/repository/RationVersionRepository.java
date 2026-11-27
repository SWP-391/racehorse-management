package vn.racehorse.ration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.ration.entity.RationVersion;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RationVersionRepository extends JpaRepository<RationVersion, Long> {

    List<RationVersion> findByHorseIdOrderByVersionNoDesc(Long horseId);

    Optional<RationVersion> findFirstByHorseIdOrderByVersionNoDesc(Long horseId);

    List<RationVersion> findByHorseIdInOrderByVersionNoDesc(Collection<Long> horseIds);

    /** The version in force on a date: the latest whose effective date is not after it (BR-140, BR-173). */
    Optional<RationVersion> findFirstByHorseIdAndEffectiveDateLessThanEqualOrderByVersionNoDesc(Long horseId, LocalDate date);

    /** Horse id and tag of the latest version of each given horse, without loading meals or history. */
    @org.springframework.data.jpa.repository.Query(value = "select v.horse_id, v.condition_tag from ration_version v "
            + "where v.horse_id in (:ids) and v.version_no = (select max(x.version_no) from ration_version x "
            + "where x.horse_id = v.horse_id)", nativeQuery = true)
    List<Object[]> latestTags(@org.springframework.data.repository.query.Param("ids") Collection<Long> horseIds);
}
