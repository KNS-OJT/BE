package com.p03.inventory.adjustment;

import com.p03.inventory.adjustment.dto.*;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.security.Authz;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock-adjustments")
public class StockAdjustmentController {
    private final StockAdjustmentService service;

    public StockAdjustmentController(StockAdjustmentService service) {
        this.service = service;
    }

    @PreAuthorize(Authz.WAREHOUSE_OR_MANAGER)
    @GetMapping
    public PageResponse<StockAdjustmentResponse> list(@RequestParam(required = false) StockAdjustmentStatus status,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return service.list(status, Paging.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @PreAuthorize(Authz.WAREHOUSE_OR_MANAGER)
    @GetMapping("/{id}")
    public StockAdjustmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockAdjustmentResponse create(@Valid @RequestBody StockAdjustmentCreateRequest request) {
        return service.create(request);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PutMapping("/{id}")
    public StockAdjustmentResponse update(@PathVariable Long id,
                                         @Valid @RequestBody StockAdjustmentUpdateRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize(Authz.MANAGER)
    @PostMapping("/{id}/approve")
    public StockAdjustmentResponse approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping("/{id}/cancel")
    public StockAdjustmentResponse cancel(@PathVariable Long id,
                                          @Valid @RequestBody StockAdjustmentDecisionRequest request) {
        return service.cancel(id, request);
    }
}
