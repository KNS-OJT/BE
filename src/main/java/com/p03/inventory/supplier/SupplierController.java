package com.p03.inventory.supplier;

import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.security.Authz;
import com.p03.inventory.supplier.dto.SupplierCreateRequest;
import com.p03.inventory.supplier.dto.SupplierResponse;
import com.p03.inventory.supplier.dto.SupplierUpdateRequest;
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

/** IN03. Purchasing, Manager xem; chỉ Purchasing tạo/sửa/ngừng hợp tác. Không có DELETE. */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    /** GET /api/suppliers?q=&isActive=&page=&size= */
    @PreAuthorize(Authz.PURCHASING_OR_MANAGER)
    @GetMapping
    public PageResponse<SupplierResponse> search(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Boolean isActive,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return supplierService.search(q, isActive, Paging.of(page, size, Sort.by("name").and(Sort.by("id"))));
    }

    @PreAuthorize(Authz.PURCHASING_OR_MANAGER)
    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable Long id) {
        return supplierService.get(id);
    }

    @PreAuthorize(Authz.PURCHASING)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierCreateRequest request) {
        return supplierService.create(request);
    }

    @PreAuthorize(Authz.PURCHASING)
    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierUpdateRequest request) {
        return supplierService.update(id, request);
    }
}
