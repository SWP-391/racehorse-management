package vn.racehorse.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.stock.entity.SupplyPrice;

import java.util.List;

public interface SupplyPriceRepository extends JpaRepository<SupplyPrice, Long> {

    List<SupplyPrice> findBySupplyItemIdOrderByIdDesc(Long supplyItemId);
}
