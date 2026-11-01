package vn.racehorse.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.stock.entity.StockNotice;

import java.time.LocalDate;

public interface StockNoticeRepository extends JpaRepository<StockNotice, LocalDate> {
}
