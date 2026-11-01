package vn.racehorse.stable.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.stable.entity.RestockProposal;

import java.util.List;
import java.util.Optional;

public interface RestockProposalRepository extends JpaRepository<RestockProposal, Long> {

    List<RestockProposal> findAllByOrderByCreatedAtDescIdDesc();

    List<RestockProposal> findByGroomIdOrderByCreatedAtDescIdDesc(Long groomId);

    /** Row lock for a decision, so two concurrent decisions cannot both pass the PENDING check (BR-132). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RestockProposal p where p.id = :id")
    Optional<RestockProposal> findForDecision(@Param("id") Long id);
}
