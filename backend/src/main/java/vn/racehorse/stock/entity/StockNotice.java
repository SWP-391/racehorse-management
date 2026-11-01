package vn.racehorse.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** The club date for which the daily stock notice was sent, so it goes out once per date (BR-176). */
@Entity
@Table(name = "stock_notice")
public class StockNotice {

    @Id
    @Column(name = "notice_date")
    private LocalDate noticeDate;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(nullable = false)
    private int items;

    protected StockNotice() {
    }

    public StockNotice(LocalDate noticeDate, Instant sentAt, int items) {
        this.noticeDate = noticeDate;
        this.sentAt = sentAt;
        this.items = items;
    }
}
