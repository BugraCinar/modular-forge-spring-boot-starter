package dev.modularforge.shared;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
public final class PaginationUtils {

    public static final int MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
    }

    public static Pageable pageRequest(int requestedPage, int requestedSize, Sort sort) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, MAX_PAGE_SIZE));
        return PageRequest.of(page, size, sort);
    }
}
