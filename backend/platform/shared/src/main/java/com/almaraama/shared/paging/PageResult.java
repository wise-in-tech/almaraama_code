package com.almaraama.shared.paging;

import java.util.List;
import java.util.function.Function;

/** One page of results with its position (contract {@code PageMeta}). */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        items = List.copyOf(items);
    }

    /** Number of pages for this page size. */
    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }

    /** The same page with each item converted, for example from an aggregate to a response body. */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(items.stream().<R>map(mapper).toList(), page, size, totalElements);
    }
}
