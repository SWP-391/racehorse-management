package vn.racehorse.security.recovery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The short-lived single-use proof that step 1 of the code flow passed; only its hash is kept (BR-255, TECH-F7). */
@Entity
@Table(name = "code_token")
public class CodeToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected CodeToken() {
    }

    public CodeToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void use(Instant now) {
        this.usedAt = now;
    }
}
