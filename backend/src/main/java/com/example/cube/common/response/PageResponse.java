package com.example.cube.common.response;

import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

@Getter
public class PageResponse<T> {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PageResponse(List<T> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }

    public static <T> PageResponse<T> of(Page<?> source, List<T> content) {
        return new PageResponse<>(content, source.getNumber(), source.getSize(), source.getTotalElements());
    }

    /** Builds a bounded page request: negative pages become 0, size is clamped to 1..MAX_SIZE. */
    public static Pageable pageable(Integer page, Integer size, Sort sort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, sort);
    }
}
