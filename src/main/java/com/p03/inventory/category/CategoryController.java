package com.p03.inventory.category;

import com.p03.inventory.category.dto.CategoryRequest;
import com.p03.inventory.category.dto.CategoryResponse;
import com.p03.inventory.common.security.Authz;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Nhóm hàng: mọi vai trò xem (lọc sản phẩm – IN15); Warehouse, Manager tạo/sửa (IN02). */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PreAuthorize(Authz.AUTHENTICATED)
    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.list();
    }

    @PreAuthorize(Authz.WAREHOUSE_OR_MANAGER)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @PreAuthorize(Authz.WAREHOUSE_OR_MANAGER)
    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }
}
