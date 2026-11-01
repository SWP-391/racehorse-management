package vn.racehorse.stable.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.stable.entity.SupplyCategory;
import vn.racehorse.stable.entity.SupplyItem;

import java.util.Collection;
import java.util.List;

public interface SupplyItemRepository extends JpaRepository<SupplyItem, Long> {

    List<SupplyItem> findAllByOrderByNameAsc();

    List<SupplyItem> findByActiveTrueOrderByNameAsc();

    /** Same name in the same category, ignoring case, matching the unique index (BR-130). */
    @Query("select i from SupplyItem i where lower(i.name) = lower(:name) and i.category = :category")
    List<SupplyItem> findByNameAndCategory(@Param("name") String name, @Param("category") SupplyCategory category);

    /** The rows of the given items under a write lock, in id order so two writers never wait on each other (TECH-153). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SupplyItem i where i.id in :ids order by i.id")
    List<SupplyItem> lockAllById(@Param("ids") Collection<Long> ids);
}
