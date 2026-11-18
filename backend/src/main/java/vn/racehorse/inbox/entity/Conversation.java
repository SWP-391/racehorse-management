package vn.racehorse.inbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A conversation between allowed people, or between a person and the Club Manager role (BR-227, BR-231). */
@Entity
@Table(name = "conversation")
public class Conversation {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
    public static final String CLUB_MANAGER = "CLUB_MANAGER";
    public static final String MESSAGE = "MESSAGE";
    public static final String FEEDBACK = "FEEDBACK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subject;

    @Column(name = "horse_id")
    private Long horseId;

    @Column(name = "to_role")
    private String toRole;

    @Column(nullable = false)
    private String kind = MESSAGE;

    @Column(name = "started_by_id", nullable = false)
    private Long startedById;

    @Column(nullable = false)
    private String state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "state_by_id")
    private Long stateById;

    @Column(name = "state_at")
    private Instant stateAt;

    protected Conversation() {
    }

    public Conversation(String subject, Long horseId, String toRole, Long startedById, Instant now) {
        this.subject = subject;
        this.horseId = horseId;
        this.toRole = toRole;
        this.startedById = startedById;
        this.state = OPEN;
        this.createdAt = now;
    }

    public Conversation(String subject, Long horseId, String toRole, Long startedById, Instant now, String kind) {
        this(subject, horseId, toRole, startedById, now);
        this.kind = kind;
    }

    public String getKind() {
        return kind;
    }

    public boolean isOpen() {
        return OPEN.equals(state);
    }

    /** Closes or reopens, keeping who did it and when (BR-232). */
    public void changeState(boolean open, Long byId, Instant at) {
        this.state = open ? OPEN : CLOSED;
        this.stateById = byId;
        this.stateAt = at;
    }

    public Long getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public Long getHorseId() {
        return horseId;
    }

    public String getToRole() {
        return toRole;
    }

    public Long getStartedById() {
        return startedById;
    }

    public String getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getStateById() {
        return stateById;
    }

    public Instant getStateAt() {
        return stateAt;
    }
}
