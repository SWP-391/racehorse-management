package vn.racehorse.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.billing.entity.ExamFeeType;

import java.util.List;

public interface ExamFeeTypeRepository extends JpaRepository<ExamFeeType, Long> {

    List<ExamFeeType> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
