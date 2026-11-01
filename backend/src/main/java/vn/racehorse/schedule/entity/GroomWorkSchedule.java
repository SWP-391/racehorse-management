package vn.racehorse.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One range of a Groom's fixed weekly barn hours, in minutes since midnight so that 24:00 is a valid end; weekday is
 * ISO (Monday = 1). The same every week (BR-166, TECH-144).
 */
@Entity
@Table(name = "groom_work_schedule")
public class GroomWorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private int weekday;

    @Column(name = "start_min", nullable = false)
    private int startMin;

    @Column(name = "end_min", nullable = false)
    private int endMin;

    protected GroomWorkSchedule() {
    }

    public GroomWorkSchedule(Long accountId, int weekday, int startMin, int endMin) {
        this.accountId = accountId;
        this.weekday = weekday;
        this.startMin = startMin;
        this.endMin = endMin;
    }

    public Long getAccountId() {
        return accountId;
    }

    public int getWeekday() {
        return weekday;
    }

    public int getStartMin() {
        return startMin;
    }

    public int getEndMin() {
        return endMin;
    }
}
