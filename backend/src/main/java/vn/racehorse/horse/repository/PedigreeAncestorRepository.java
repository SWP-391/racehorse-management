package vn.racehorse.horse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.horse.entity.PedigreeAncestor;

import java.util.List;

public interface PedigreeAncestorRepository extends JpaRepository<PedigreeAncestor, Long> {

    List<PedigreeAncestor> findByHorseId(Long horseId);

    @Modifying
    @Query("delete from PedigreeAncestor a where a.horseId = :horseId")
    void deleteByHorseId(@Param("horseId") Long horseId);
}
