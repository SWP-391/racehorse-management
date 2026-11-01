package vn.racehorse.horse.entity;

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
import jakarta.persistence.Table;

/** One known ancestor: either a horse in the system or a name only (TECH-010). */
@Entity
@Table(name = "pedigree_ancestor")
public class PedigreeAncestor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PedigreePosition position;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ancestor_horse_id")
    private Horse ancestorHorse;

    @Column(name = "ancestor_name")
    private String ancestorName;

    protected PedigreeAncestor() {
    }

    public PedigreeAncestor(Long horseId, PedigreePosition position, Horse ancestorHorse, String ancestorName) {
        this.horseId = horseId;
        this.position = position;
        this.ancestorHorse = ancestorHorse;
        this.ancestorName = ancestorName;
    }

    public PedigreePosition getPosition() {
        return position;
    }

    public Horse getAncestorHorse() {
        return ancestorHorse;
    }

    public String getAncestorName() {
        return ancestorName;
    }
}
