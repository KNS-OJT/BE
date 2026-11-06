package com.p03.inventory.category;

import com.p03.inventory.category.dto.CategoryRequest;
import com.p03.inventory.category.dto.CategoryResponse;
import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.common.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Nhóm hàng (IN02, IN15). Tên duy nhất không phân biệt hoa thường; không xóa vật lý. */
@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    /** Warehouse/Manager thấy cả nhóm đã ngừng; vai trò khác chỉ thấy nhóm đang dùng. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        boolean manageable = CurrentUser.is(Role.WAREHOUSE_STAFF) || CurrentUser.is(Role.MANAGER);
        List<Category> rows = manageable ? categories.findAllByOrderByNameAsc()
                : categories.findAllByActiveTrueOrderByNameAsc();
        return rows.stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.duplicate("Tên nhóm hàng đã tồn tại");
        }
        Category c = new Category();
        c.setName(name);
        c.setDescription(Text.trimToNull(req.description()));
        c.setActive(req.active() == null || req.active());
        return CategoryResponse.from(categories.saveAndFlush(c));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = categories.findById(id).orElseThrow(ApiException::notFound);
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.duplicate("Tên nhóm hàng đã tồn tại");
        }
        c.setName(name);
        c.setDescription(Text.trimToNull(req.description()));
        if (req.active() != null) {
            c.setActive(req.active());
        }
        return CategoryResponse.from(categories.saveAndFlush(c));
    }
}
