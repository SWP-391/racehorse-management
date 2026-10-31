package vn.racehorse.common.paging;

import java.util.List;

/**
 * Pages a list that the service has already reduced to what the caller may see, so `total` and an empty
 * result never reveal a hidden row (BR-185, AC-370). Order is the caller's order, which stays fixed per list.
 */
public final class Paging {

    private Paging() {
    }

    public static <T> PageResponse<T> page(List<T> visible, PageParams params) {
        long total = visible.size();
        int from = (int) Math.min((long) params.page() * params.size(), total);
        int to = (int) Math.min(from + (long) params.size(), total);
        return new PageResponse<>(visible.subList(from, to), params.page(), params.size(), total);
    }
}
