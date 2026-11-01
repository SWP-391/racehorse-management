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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** A meal of a ration version (BR-138). */
@Entity
@Table(name = "ration_meal")
public class RationMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    private RationVersion version;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String name;

    @Column(name = "meal_time", nullable = false)
    private LocalTime time;

    @OneToMany(mappedBy = "meal", cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<RationItem> items = new ArrayList<>();

    protected RationMeal() {
    }

    public RationMeal(RationVersion version, int position, String name, LocalTime time) {
        this.version = version;
        this.position = position;
        this.name = name;
        this.time = time;
    }

    public void addItem(RationItem item) {
        items.add(item);
    }

    public Long getId() { return id; }
    public int getPosition() { return position; }
    public String getName() { return name; }
    public LocalTime getTime() { return time; }
    public List<RationItem> getItems() { return items; }
}
