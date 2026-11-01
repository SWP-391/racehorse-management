package vn.racehorse.veterinary.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.veterinary.entity.Examination;

import java.util.List;

public interface ExaminationRepository extends JpaRepository<Examination, Long> {

    List<Examination> findByHorseIdOrderByExamDateDescIdDesc(Long horseId);

    List<Examination> findByHorseIdAndWeightKgIsNotNullOrderByExamDateAscIdAsc(Long horseId);
}
