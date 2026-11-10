package com.p03.inventory.common.paging;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Định dạng trang dữ liệu dùng chung cho MỌI API danh sách. Không trả thẳng {@code Page<Entity>} của Spring
 * (lộ cấu trúc nội bộ, JSON không ổn định giữa các phiên bản).
 *
 * @param page số trang, bắt đầu từ 0
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> items = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
