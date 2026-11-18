package vn.racehorse.veterinary.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.veterinary.entity.Examination;

import java.util.List;

public interface ExaminationRepository extends JpaRepository<Examination, Long> {

    List<Examination> findByHorseIdOrderByExamDateDescIdDesc(Long horseId);

    List<Examination> findByHorseIdAndWeightKgIsNotNullOrderByExamDateAscIdAsc(Long horseId);

    /** Has this Veterinarian recorded an examination of the horse (the link of BR-228 between owner and Veterinarian). */
    boolean existsByHorseIdAndRecordedById(Long horseId, Long recordedById);

    /** The Veterinarians who recorded an examination of the horse. */
    @Query("select distinct e.recordedBy.id from Examination e where e.horseId = :horseId")
    List<Long> examinerIds(@Param("horseId") Long horseId);
}
