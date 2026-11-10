package com.p03.inventory.product;

import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.security.Authz;
import com.p03.inventory.product.dto.MinStockRequest;
import com.p03.inventory.product.dto.MinStockResponse;
import com.p03.inventory.product.dto.ProductCreateRequest;
import com.p03.inventory.product.dto.ProductResponse;
import com.p03.inventory.product.dto.ProductUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SCR-40. Mọi vai trò tìm/xem (IN15); Warehouse, Manager tạo/sửa (IN02) và đặt ngưỡng tồn (IN05). */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** GET /api/products?q=&categoryId=&activeOnly=&page=&size= */
    @PreAuthorize(Authz.AUTHENTICATED)
    @GetMapping
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) Boolean activeOnly,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return productService.search(q, categoryId, activeOnly, Paging.of(page, size, Sort.by("sku")));
    }

    @PreAuthorize(Authz.AUTHENTICATED)
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PreAuthorize(Authz.WAREHOUSE_OR_MANAGER)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductCreateRequest request) {
        return productService.create(request);
    }

    @PreAuthorize(Authz.WAREHOUSE_OR_MANAGER)
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return productService.update(id, request);
    }

    @PreAuthorize(Authz.WAREHOUSE_OR_MANAGER)
    @PutMapping("/{id}/min-stock")
    public MinStockResponse updateMinimumStock(@PathVariable Long id, @Valid @RequestBody MinStockRequest request) {
        return productService.updateMinimumStock(id, request);
    }
}
