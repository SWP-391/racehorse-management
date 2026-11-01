package vn.racehorse.stable.dto;

import vn.racehorse.stable.entity.ProposalState;
import vn.racehorse.stable.entity.RestockProposal;

import java.time.Instant;

public record ProposalDto(Long id, Long itemId, String itemName, String unit, String groomName, long quantity,
                          String note, ProposalState state, Instant createdAt, String decidedByName,
                          Instant decidedAt, String decisionReason) {
    public static ProposalDto of(RestockProposal p) {
        return new ProposalDto(p.getId(), p.getItem().getId(), p.getItem().getName(), p.getItem().getUnit(),
                p.getGroom().getDisplayName(), p.getQuantity(), p.getNote(), p.getState(), p.getCreatedAt(),
                p.getDecidedBy() == null ? null : p.getDecidedBy().getDisplayName(), p.getDecidedAt(),
                p.getDecisionReason());
    }
}
