package vn.racehorse.common.paging;

import org.springframework.http.HttpStatus;
import vn.racehorse.common.exception.ApiException;

import java.util.Map;

/** The paging and search parameters every list takes: page (0-based), size (default 20, at most 100) and q (TECH-173). */
public record PageParams(int page, int size, String q) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** The largest page a caller may ask for; 100 in production, raised only by tests that read whole lists. */
    private static volatile int maxSize = MAX_SIZE;

    public static void setMaxSize(int max) {
        maxSize = max;
    }

    public static int maxSize() {
        return maxSize;
    }

    public static PageParams of(Integer page, Integer size, String q) {
        if (size != null && (size < 1 || size > maxSize)) {
            String message = "Kích thước trang phải từ 1 đến " + maxSize + ".";
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, Map.of("size", message));
        }
        if (page != null && page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Số trang không hợp lệ.",
                    Map.of("page", "Số trang không hợp lệ."));
        }
        String query = q == null || q.isBlank() ? null : q.trim();
        return new PageParams(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size, query);
    }
}
