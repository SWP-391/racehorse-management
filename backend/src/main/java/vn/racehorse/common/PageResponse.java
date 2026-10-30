package vn.racehorse.common;

import java.util.List;

/** One page of a list plus the number of rows that match the filters (TECH-173). */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
