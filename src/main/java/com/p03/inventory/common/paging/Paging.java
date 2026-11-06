package com.p03.inventory.common.paging;

import com.p03.inventory.common.error.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Chuẩn hóa tham số phân trang {@code ?page=&size=} (SDD mục 4, IA-NF04): page bắt đầu từ 0,
 * size mặc định 20, tối đa 100. Giá trị sai → 400 VALIDATION_ERROR (không lặng lẽ cắt bớt).
 *
 * <pre>
 * &#64;GetMapping
 * public PageResponse&lt;PrSummary&gt; list(@RequestParam(required = false) Integer page,
 *                                    @RequestParam(required = false) Integer size) {
 *     return service.list(Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
 * }
 * </pre>
 */
public final class Paging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static Pageable of(Integer page, Integer size) {
        return of(page, size, Sort.unsorted());
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) {
            throw ApiException.validation("page phải >= 0");
        }
        if (s < 1 || s > MAX_SIZE) {
            throw ApiException.validation("size phải trong khoảng 1.." + MAX_SIZE);
        }
        return PageRequest.of(p, s, sort == null ? Sort.unsorted() : sort);
    }
}
