package vn.racehorse.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.schedule.entity.GroomWorkSchedule;

import java.util.List;

public interface GroomWorkScheduleRepository extends JpaRepository<GroomWorkSchedule, Long> {

    List<GroomWorkSchedule> findByAccountIdOrderByWeekdayAscStartMinAsc(Long accountId);

    List<GroomWorkSchedule> findByAccountIdAndWeekday(Long accountId, int weekday);

    long countByAccountId(Long accountId);

    void deleteByAccountId(Long accountId);
}
