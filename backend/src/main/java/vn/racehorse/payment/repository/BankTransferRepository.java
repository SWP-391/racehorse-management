package vn.racehorse.payment.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.payment.entity.BankTransfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BankTransferRepository extends JpaRepository<BankTransfer, Long> {

    /**
     * Records a transfer once: a second insert of the same (provider, provider_id) does nothing and returns 0, and
     * a concurrent insert waits for the first one to finish (BR-220, TECH-233).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO bank_transfer (provider, provider_id, amount, content, account_number, reference, "
            + "transferred_at, received_at, simulated, status) VALUES (:provider, :providerId, :amount, :content, "
            + ":accountNumber, :reference, :transferredAt, :receivedAt, :simulated, 'UNMATCHED') "
            + "ON CONFLICT (provider, provider_id) DO NOTHING", nativeQuery = true)
    int insertOnce(@Param("provider") String provider, @Param("providerId") String providerId,
                   @Param("amount") BigDecimal amount, @Param("content") String content,
                   @Param("accountNumber") String accountNumber, @Param("reference") String reference,
                   @Param("transferredAt") Instant transferredAt, @Param("receivedAt") Instant receivedAt,
                   @Param("simulated") boolean simulated);

    Optional<BankTransfer> findByProviderAndProviderId(String provider, String providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BankTransfer t where t.id = :id")
    Optional<BankTransfer> findByIdForUpdate(@Param("id") Long id);

    List<BankTransfer> findByStatusInOrderByReceivedAtDescIdDesc(List<String> statuses);
}
