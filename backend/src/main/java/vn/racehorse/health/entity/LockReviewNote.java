package vn.racehorse.health.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.racehorse.account.entity.UserAccount;

import java.time.Instant;

/** A Head Trainer's note asking the Veterinarian to review a lock (BR-016). */
@Entity
@Table(name = "lock_review_note")
public class LockReviewNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lock_id", nullable = false)
    private Long lockId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserAccount author;

    @Column(nullable = false)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LockReviewNote() {
    }

    public LockReviewNote(Long lockId, UserAccount author, String note, Instant createdAt) {
        this.lockId = lockId;
        this.author = author;
        this.note = note;
        this.createdAt = createdAt;
    }

    public UserAccount getAuthor() {
        return author;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
