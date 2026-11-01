package vn.racehorse.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.billing.entity.BillingMonth;

public interface BillingMonthRepository extends JpaRepository<BillingMonth, String> {
}
