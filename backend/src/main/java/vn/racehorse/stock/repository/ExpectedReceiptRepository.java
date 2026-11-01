package vn.racehorse.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.stock.entity.ExpectedReceipt;

import java.time.LocalDate;
import java.util.List;

public interface ExpectedReceiptRepository extends JpaRepository<ExpectedReceipt, Long> {

    List<ExpectedReceipt> findBySupplyItemIdAndStateOrderByExpectedDateAsc(Long supplyItemId, ExpectedReceipt.State state);

    /** The deliveries still counted by the forecast: expected and not yet past their date (BR-177). */
    List<ExpectedReceipt> findByStateAndExpectedDateGreaterThanEqual(ExpectedReceipt.State state, LocalDate from);
}
