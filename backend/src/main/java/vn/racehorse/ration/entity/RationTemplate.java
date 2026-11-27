package vn.racehorse.ration.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import vn.racehorse.account.entity.UserAccount;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A ration template of the Veterinarian: switched off, never deleted (BR-249). */
@Entity
@Table(name = "ration_template")
public class RationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RationTag tag;

    @Column(nullable = false)
    private boolean active = true;

    /** Waits for the Veterinarian to edit or confirm it: "chờ xác nhận" (BR-265). */
    @Column(name = "needs_confirmation", nullable = false)
    private boolean needsConfirmation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<RationTemplateMeal> meals = new ArrayList<>();

    protected RationTemplate() {
    }

    public RationTemplate(String name, RationTag tag, UserAccount createdBy, Instant createdAt) {
        this.name = name;
        this.tag = tag;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void rename(String name, RationTag tag) {
        this.name = name;
        this.tag = tag;
    }

    public void confirm() {
        this.needsConfirmation = false;
    }

    public void deactivate() {
        this.active = false;
    }

    public void clearMeals() {
        meals.clear();
    }

    public void addMeal(RationTemplateMeal meal) {
        meals.add(meal);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public RationTag getTag() { return tag; }
    public boolean isActive() { return active; }
    public boolean isNeedsConfirmation() { return needsConfirmation; }
    public List<RationTemplateMeal> getMeals() { return meals; }
}
