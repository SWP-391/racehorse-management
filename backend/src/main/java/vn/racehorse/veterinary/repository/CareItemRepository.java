package vn.racehorse.veterinary.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.veterinary.entity.CareItem;

import java.time.LocalDate;
import java.util.List;

public interface CareItemRepository extends JpaRepository<CareItem, Long> {

    List<CareItem> findByHorseIdOrderByNextDueAscIdAsc(Long horseId);

    List<CareItem> findByNextDueLessThanEqualOrderByNextDueAscIdAsc(LocalDate date);

    /** Items with a planned medicine that fall due on or before the date: the forecast's care demand (BR-175). */
    List<CareItem> findByPlannedSupplyItemIdIsNotNullAndNextDueLessThanEqual(LocalDate date);
}
