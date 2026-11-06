package vn.racehorse.injury.entity;

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
import vn.racehorse.account.entity.UserAccount;

import java.time.Instant;
import java.time.LocalDate;

/** One step in a mark's history: its creation or a status change (BR-097). Append-only. */
@Entity
@Table(name = "injury_mark_event")
public class InjuryMarkEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mark_id", nullable = false)
    private Long markId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private MarkStatus from;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private MarkStatus to;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    private String note;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "by_id", nullable = false)
    private UserAccount by;

    @Column(nullable = false)
    private Instant at;

    protected InjuryMarkEvent() {
    }

    public InjuryMarkEvent(Long markId, MarkStatus from, MarkStatus to, LocalDate eventDate, String note, UserAccount by,
                    Instant at) {
        this.markId = markId;
        this.from = from;
        this.to = to;
        this.eventDate = eventDate;
        this.note = note;
        this.by = by;
        this.at = at;
    }

    public Long getId() { return id; }
    public Long getMarkId() { return markId; }
    public MarkStatus getFrom() { return from; }
    public MarkStatus getTo() { return to; }
    public LocalDate getEventDate() { return eventDate; }
    public String getNote() { return note; }
    public UserAccount getBy() { return by; }
    public Instant getAt() { return at; }
}
