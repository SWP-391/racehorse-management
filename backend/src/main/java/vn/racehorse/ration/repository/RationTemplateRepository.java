package vn.racehorse.ration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.ration.entity.RationTemplate;

import java.util.List;

public interface RationTemplateRepository extends JpaRepository<RationTemplate, Long> {

    List<RationTemplate> findAllByOrderByActiveDescNameAsc();

    boolean existsByNameIgnoreCaseAndActiveTrueAndIdNot(String name, Long id);
}
