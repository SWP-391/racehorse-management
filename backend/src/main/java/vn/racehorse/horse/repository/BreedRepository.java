package vn.racehorse.horse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.horse.entity.Breed;

import java.util.List;
import java.util.Optional;

public interface BreedRepository extends JpaRepository<Breed, Long> {

    List<Breed> findAllByOrderByNameAsc();

    /** Same name ignoring case and surrounding spaces, matching the unique index of V28. */
    @Query("select b from Breed b where lower(trim(b.name)) = lower(trim(:name))")
    Optional<Breed> findByNameIgnoreCase(@Param("name") String name);
}
