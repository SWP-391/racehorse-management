package vn.racehorse.billing.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** A kind of examination or treatment with its own fee; the price is kept apart (BR-178). */
@Entity
@Table(name = "exam_fee_type")
public class ExamFeeType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExamFeeType() {
    }

    public ExamFeeType(String name, Instant createdAt) {
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void deactivate() {
        this.active = false;
    }
}
