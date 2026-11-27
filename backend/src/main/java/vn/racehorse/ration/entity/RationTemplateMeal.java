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

/** A meal of a ration template. */
@Entity
@Table(name = "ration_template_meal")
public class RationTemplateMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private RationTemplate template;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String name;

    @Column(name = "meal_time", nullable = false)
    private LocalTime time;

    @OneToMany(mappedBy = "meal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<RationTemplateItem> items = new ArrayList<>();

    protected RationTemplateMeal() {
    }

    public RationTemplateMeal(RationTemplate template, int position, String name, LocalTime time) {
        this.template = template;
        this.position = position;
        this.name = name;
        this.time = time;
    }

    public void addItem(RationTemplateItem item) {
        items.add(item);
    }

    public int getPosition() { return position; }
    public String getName() { return name; }
    public LocalTime getTime() { return time; }
    public List<RationTemplateItem> getItems() { return items; }
}
