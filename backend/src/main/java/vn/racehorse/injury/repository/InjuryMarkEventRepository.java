package vn.racehorse.injury.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.injury.entity.InjuryMarkEvent;

import java.util.List;

public interface InjuryMarkEventRepository extends JpaRepository<InjuryMarkEvent, Long> {

    List<InjuryMarkEvent> findByMarkIdInOrderByIdAsc(List<Long> markIds);
}
