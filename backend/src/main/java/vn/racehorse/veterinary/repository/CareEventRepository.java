package vn.racehorse.veterinary.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.veterinary.entity.CareEvent;

import java.util.Collection;
import java.util.List;

public interface CareEventRepository extends JpaRepository<CareEvent, Long> {

    List<CareEvent> findByItemIdOrderByAtAscIdAsc(Long itemId);

    /** Every event of the given items, newest first: one query for a whole page (TECH-181). */
    @Query("select e from CareEvent e where e.itemId in :ids order by e.at desc, e.id desc")
    List<CareEvent> findForItems(@Param("ids") Collection<Long> ids);
}
