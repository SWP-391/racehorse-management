package vn.racehorse.stable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.racehorse.account.entity.UserAccount;

import java.time.Instant;

/** A Groom's request to restock an item, decided once by the Club Manager (BR-131, BR-132). */
@Entity
@Table(name = "restock_proposal")
public class RestockProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private SupplyItem item;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "groom_id", nullable = false)
    private UserAccount groom;

    @Column(nullable = false)
    private long quantity;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalState state;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "decided_by")
    private UserAccount decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RestockProposal() {
    }

    public RestockProposal(SupplyItem item, UserAccount groom, long quantity, String note, Instant createdAt) {
        this.item = item;
        this.groom = groom;
        this.quantity = quantity;
        this.note = note;
        this.state = ProposalState.PENDING;
        this.createdAt = createdAt;
    }

    public void decide(ProposalState to, UserAccount by, String reason, Instant at) {
        this.state = to;
        this.decidedBy = by;
        this.decisionReason = reason;
        this.decidedAt = at;
    }

    public Long getId() { return id; }
    public SupplyItem getItem() { return item; }
    public UserAccount getGroom() { return groom; }
    public long getQuantity() { return quantity; }
    public String getNote() { return note; }
    public ProposalState getState() { return state; }
    public UserAccount getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getCreatedAt() { return createdAt; }
}
