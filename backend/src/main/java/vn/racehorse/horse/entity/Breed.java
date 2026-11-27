package vn.racehorse.horse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One entry of the breed catalog (BR-246). Never deleted; the protected entry "Khác" is never deactivated. */
@Entity
@Table(name = "breed")
public class Breed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "protected_entry", nullable = false)
    private boolean protectedEntry;

    protected Breed() {
    }

    public Breed(String name) {
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public boolean isProtectedEntry() {
        return protectedEntry;
    }
}
