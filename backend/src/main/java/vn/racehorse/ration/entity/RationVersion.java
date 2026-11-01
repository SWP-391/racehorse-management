package vn.racehorse.ration.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One saved version of a horse's ration, never changed afterwards (BR-140). */
@Entity
@Table(name = "ration_version")
public class RationVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "saved_by")
    private UserAccount savedBy;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<RationMeal> meals = new ArrayList<>();

    protected RationVersion() {
    }

    public RationVersion(Long horseId, int versionNo, LocalDate effectiveDate, UserAccount savedBy, Instant savedAt) {
        this.horseId = horseId;
        this.versionNo = versionNo;
        this.effectiveDate = effectiveDate;
        this.savedBy = savedBy;
        this.savedAt = savedAt;
    }

    public void addMeal(RationMeal meal) {
        meals.add(meal);
    }

    public Long getId() { return id; }
    public Long getHorseId() { return horseId; }
    public int getVersionNo() { return versionNo; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public UserAccount getSavedBy() { return savedBy; }
    public Instant getSavedAt() { return savedAt; }
    public List<RationMeal> getMeals() { return meals; }
}
