package vn.racehorse.horse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.horse.entity.Area;

import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<Area, Long> {

    List<Area> findAllByOrderByNameAsc();

    /** Locks the area row so a resize, a delete and a placement into it take turns (TECH-142). */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Area a where a.id = :id")
    Optional<Area> findByIdForUpdate(@Param("id") Long id);

    @Query("select a from Area a where lower(a.name) = lower(:name)")
    Optional<Area> findByNameIgnoreCase(@Param("name") String name);
}
