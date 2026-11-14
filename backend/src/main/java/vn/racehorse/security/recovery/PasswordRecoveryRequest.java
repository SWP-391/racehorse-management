package vn.racehorse.security.recovery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A recovery request for an address, known or not, kept only to pace the requests (BR-214, BR-215). */
@Entity
@Table(name = "password_recovery_request")
public class PasswordRecoveryRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    protected PasswordRecoveryRequest() {
    }

    public PasswordRecoveryRequest(String email, Instant requestedAt) {
        this.email = email;
        this.requestedAt = requestedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
