package vn.racehorse.stock.dto;

/** {@code evaluated} is false before 07:00, when the date was already sent, or when nothing qualifies. */
public record StockNoticeResult(boolean sent, int items, int medicines) {
    public static final StockNoticeResult NONE = new StockNoticeResult(false, 0, 0);
}
