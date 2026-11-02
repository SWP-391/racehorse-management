package vn.racehorse.stable.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.stable.entity.SupplyUnit;

import java.util.List;
import java.util.Optional;

public interface SupplyUnitRepository extends JpaRepository<SupplyUnit, Long> {

    List<SupplyUnit> findAllByOrderByNameAsc();

    /** The same name ignoring case, matching the unique index (BR-195). */
    @Query("select u from SupplyUnit u where lower(u.name) = lower(:name)")
    Optional<SupplyUnit> findByNameIgnoreCase(@Param("name") String name);

    /** Items per unit, for the catalog screen. */
    @Query("select i.unitRef.id, count(i) from SupplyItem i group by i.unitRef.id")
    List<Object[]> itemCounts();
}
