package vn.racehorse.stock.exception;

import java.util.List;

/** A use that stock cannot cover; nothing was written (BR-173, BR-172). */
public class InsufficientStockException extends RuntimeException {

    public record Shortage(Long itemId, String name, String unit, long requested, long available) {
        public long missing() {
            return requested - available;
        }
    }

    private final transient List<Shortage> shortages;

    public InsufficientStockException(List<Shortage> shortages) {
        super("Không đủ vật tư trong kho");
        this.shortages = shortages;
    }

    public List<Shortage> getShortages() {
        return shortages;
    }
}
