package vn.racehorse.security.recovery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One recovery code of an account; only a hash of the code is kept (BR-215, TECH-224). */
@Entity
@Table(name = "password_recovery")
public class PasswordRecovery {

    /** Wrong tries after which the code is voided (BR-215). */
    public static final int MAX_TRIES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private short tries;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecoveryState state;

    protected PasswordRecovery() {
    }

    public PasswordRecovery(Long accountId, String codeHash, Instant createdAt, Instant expiresAt) {
        this.accountId = accountId;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.state = RecoveryState.LIVE;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getTries() {
        return tries;
    }

    public RecoveryState getState() {
        return state;
    }

    public boolean isLive() {
        return state == RecoveryState.LIVE;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Counts a wrong try and voids the code at the fifth. */
    public void failedTry() {
        tries++;
        if (tries >= MAX_TRIES) {
            state = RecoveryState.VOID;
        }
    }

    public void spend() {
        state = RecoveryState.SPENT;
    }

    public void voidCode() {
        state = RecoveryState.VOID;
    }
}
