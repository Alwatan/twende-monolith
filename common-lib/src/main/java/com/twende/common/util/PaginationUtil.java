package com.twende.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PaginationUtil {

    private PaginationUtil() {}

    /**
     * Build a PageRequest with sensible defaults and bounds.
     *
     * @param page zero-based page number (clamped to >= 0)
     * @param size page size (clamped to 1..100)
     * @param sortBy field to sort by (defaults to "createdAt" if null/blank)
     * @param direction sort direction (defaults to DESC if null)
     * @return a configured PageRequest
     */
    public static PageRequest buildPageRequest(
            int page, int size, String sortBy, Sort.Direction direction) {
        size = Math.min(Math.max(size, 1), 100);
        page = Math.max(page, 0);
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "createdAt";
        }
        if (direction == null) {
            direction = Sort.Direction.DESC;
        }
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}
