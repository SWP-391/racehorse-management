package vn.racehorse.stable.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.stable.entity.ChoreReminder;

import java.time.LocalDate;

public interface ChoreReminderRepository extends JpaRepository<ChoreReminder, LocalDate> {
}
