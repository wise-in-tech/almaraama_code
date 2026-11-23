package com.almaraama.shared.paging;

/** A page to read: number from 0 and size from 1 to 100 (contract parameters {@code page} and {@code size}). */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        page = Math.max(0, page);
        size = Math.clamp(size, 1, MAX_SIZE);
    }

    /** From optional request parameters: page 0 and size 20 by default, size capped at 100. */
    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(page == null ? 0 : page, size == null ? 20 : size);
    }

    /** Index of the first item of the page. */
    public long offset() {
        return (long) page * size;
    }
}
