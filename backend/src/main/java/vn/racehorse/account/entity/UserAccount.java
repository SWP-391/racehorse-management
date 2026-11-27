package vn.racehorse.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The sign-in identifier, lower case and unique; null for an account that has none yet (BR-210, BR-212). */
    @Column(unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Raised by every password reset; a session opened with an older value is refused (BR-216). */
    @Column(name = "password_version", nullable = false)
    private long passwordVersion;

    /** Optional contact number the person keeps on their own profile (BR-254). */
    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    /** Invited and not yet activated by its owner; such an account is never active (BR-236, TECH-231). */
    @Column(nullable = false)
    private boolean pending;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String displayName, String passwordHash, Role role, Instant createdAt) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    /** An account created without a password: pending, so it cannot be used until its owner activates it (BR-235). */
    public static UserAccount invited(String email, String displayName, String unusablePasswordHash, Role role,
                                      Instant createdAt) {
        UserAccount account = new UserAccount(email, displayName, unusablePasswordHash, role, createdAt);
        account.active = false;
        account.pending = true;
        return account;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    /** The email for audit details, which cannot hold a null: empty when the account has none. */
    public String emailForAudit() {
        return email == null ? "" : email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public long getPasswordVersion() {
        return passwordVersion;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPending() {
        return pending;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    /** Sets a new password and ends every session opened before it (BR-216). */
    public void resetPassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordVersion++;
    }

    /** Sets the first password of a pending account and makes it usable (BR-237). */
    public void activate(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordVersion++;
        this.pending = false;
        this.active = true;
    }

    /** Deactivating a pending account ends the invitation for good (BR-236). */
    public void deactivate() {
        this.active = false;
        this.pending = false;
    }
}
