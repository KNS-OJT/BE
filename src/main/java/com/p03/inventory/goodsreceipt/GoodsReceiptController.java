package com.p03.inventory.goodsreceipt;

import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.security.Authz;
import com.p03.inventory.goodsreceipt.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goods-receipts")
@Tag(name = "Goods Receipts", description = "Nhận hàng theo PO; trigger DB ghi tồn và ledger khi xác nhận")
public class GoodsReceiptController {
    private final GoodsReceiptService service;

    public GoodsReceiptController(GoodsReceiptService service) {
        this.service = service;
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @GetMapping
    @Operation(operationId = "grList", summary = "Danh sách phiếu nhận hàng")
    public PageResponse<GoodsReceiptResponse> list(@RequestParam(required = false) GoodsReceiptStatus status,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return service.list(status, Paging.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @GetMapping("/{id}")
    @Operation(operationId = "grGet", summary = "Chi tiết phiếu nhận hàng")
    public GoodsReceiptResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "grCreate", summary = "Lập phiếu nhận DRAFT từ PO đã phát hành")
    public GoodsReceiptResponse create(@Valid @RequestBody GoodsReceiptCreateRequest request) {
        return service.create(request);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PutMapping("/{id}")
    @Operation(operationId = "grUpdate", summary = "Cập nhật toàn bộ nội dung phiếu nhận DRAFT")
    public GoodsReceiptResponse update(@PathVariable @Positive Long id,
                                       @Valid @RequestBody GoodsReceiptUpdateRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping("/{id}/confirm")
    @Operation(operationId = "grConfirm", summary = "Xác nhận nhận hàng trong số lượng còn lại của PO")
    public GoodsReceiptResponse confirm(@PathVariable @Positive Long id,
                                        @Valid @RequestBody GoodsReceiptConfirmRequest request) {
        return service.confirm(id, request);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping("/{id}/cancel")
    @Operation(operationId = "grCancel", summary = "Hủy phiếu nhận DRAFT kèm lý do")
    public GoodsReceiptResponse cancel(@PathVariable @Positive Long id,
                                       @Valid @RequestBody GoodsReceiptDecisionRequest request) {
        return service.cancel(id, request);
    }
}
