package vn.racehorse.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.schedule.entity.GroomLeave;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GroomLeaveRepository extends JpaRepository<GroomLeave, Long> {

    Optional<GroomLeave> findByAccountIdAndLeaveDateAndCancelledAtIsNull(Long accountId, LocalDate leaveDate);

    boolean existsByAccountIdAndLeaveDateAndCancelledAtIsNull(Long accountId, LocalDate leaveDate);

    List<GroomLeave> findByAccountIdAndLeaveDateBetweenOrderByLeaveDateAscIdAsc(Long accountId, LocalDate from,
                                                                                  LocalDate to);

    List<GroomLeave> findByLeaveDateAndCancelledAtIsNull(LocalDate leaveDate);
}
