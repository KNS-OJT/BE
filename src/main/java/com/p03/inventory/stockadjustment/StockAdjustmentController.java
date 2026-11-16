package com.p03.inventory.stockadjustment;

import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.security.Authz;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentCancelRequest;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentConfirmRequest;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentCreateRequest;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentResponse;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

@RestController
@RequestMapping("/api/stock-adjustments")
@Tag(name = "Stock Adjustments", description = "IN18: Warehouse lập phiếu, Manager khác người lập xác nhận")
public class StockAdjustmentController {
    private final StockAdjustmentService service;

    public StockAdjustmentController(StockAdjustmentService service) {
        this.service = service;
    }

    @PreAuthorize(Authz.WAREHOUSE_OR_MANAGER)
    @GetMapping
    @Operation(operationId = "saList", summary = "Danh sách phiếu điều chỉnh tồn")
    public PageResponse<StockAdjustmentResponse> list(@RequestParam(required = false) StockAdjustmentStatus status,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return service.list(status, Paging.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @PreAuthorize(Authz.WAREHOUSE_OR_MANAGER)
    @GetMapping("/{id}")
    @Operation(operationId = "saGet", summary = "Chi tiết phiếu điều chỉnh tồn")
    public StockAdjustmentResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "saCreate", summary = "Warehouse lập phiếu điều chỉnh DRAFT")
    public StockAdjustmentResponse create(@Valid @RequestBody StockAdjustmentCreateRequest request) {
        return service.create(request);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PutMapping("/{id}")
    @Operation(operationId = "saUpdate", summary = "Warehouse cập nhật toàn bộ nội dung DRAFT")
    public StockAdjustmentResponse update(@PathVariable @Positive Long id,
                                          @Valid @RequestBody StockAdjustmentUpdateRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize(Authz.MANAGER)
    @PostMapping("/{id}/confirm")
    @Operation(operationId = "saConfirm", summary = "Manager xác nhận DRAFT, không tự xác nhận phiếu đã lập")
    public StockAdjustmentResponse confirm(@PathVariable @Positive Long id,
                                           @Valid @RequestBody StockAdjustmentConfirmRequest request) {
        return service.confirm(id);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping("/{id}/cancel")
    @Operation(operationId = "saCancel", summary = "Warehouse hủy DRAFT kèm lý do")
    public StockAdjustmentResponse cancel(@PathVariable @Positive Long id,
                                          @Valid @RequestBody StockAdjustmentCancelRequest request) {
        return service.cancel(id, request);
    }
}
