package vn.racehorse.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.stock.entity.StockMovement;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findBySupplyItemIdOrderByIdDesc(Long supplyItemId);

    List<StockMovement> findByRefTypeAndRefIdOrderByIdAsc(String refType, Long refId);

    boolean existsByReversesId(Long reversesId);
}
