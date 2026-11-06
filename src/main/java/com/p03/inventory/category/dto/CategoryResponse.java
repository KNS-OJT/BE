package com.p03.inventory.category.dto;

import com.p03.inventory.category.Category;

public record CategoryResponse(Long id, String name, String description, boolean active) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }
}
