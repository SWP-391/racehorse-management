package vn.racehorse.stable.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.stable.entity.Chore;
import vn.racehorse.stable.entity.ChoreEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChoreEntryRepository extends JpaRepository<ChoreEntry, Long> {

    List<ChoreEntry> findByChoreDateAndCancelledAtIsNull(LocalDate choreDate);

    List<ChoreEntry> findByChoreDateBetweenAndCancelledAtIsNull(LocalDate from, LocalDate to);

    Optional<ChoreEntry> findByHorseIdAndChoreDateAndChoreAndCancelledAtIsNull(Long horseId, LocalDate choreDate,
                                                                                Chore chore);

    List<ChoreEntry> findByHorseIdOrderByChoreDateDescMarkedAtDescIdDesc(Long horseId);
}
