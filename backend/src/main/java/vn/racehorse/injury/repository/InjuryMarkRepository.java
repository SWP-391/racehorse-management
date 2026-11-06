package vn.racehorse.injury.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.injury.entity.InjuryMark;

import java.util.List;

public interface InjuryMarkRepository extends JpaRepository<InjuryMark, Long> {

    List<InjuryMark> findByHorseIdOrderByCreatedAtDescIdDesc(Long horseId);
}
