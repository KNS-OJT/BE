package com.p03.inventory.goodsreceipt;

import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.security.Authz;
import com.p03.inventory.goodsreceipt.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goods-receipts")
public class GoodsReceiptController {
    private final GoodsReceiptService service;

    public GoodsReceiptController(GoodsReceiptService service) {
        this.service = service;
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @GetMapping
    public PageResponse<GoodsReceiptResponse> list(@RequestParam(required = false) GoodsReceiptStatus status,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return service.list(status, Paging.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @GetMapping("/{id}")
    public GoodsReceiptResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoodsReceiptResponse create(@Valid @RequestBody GoodsReceiptCreateRequest request) {
        return service.create(request);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PutMapping("/{id}")
    public GoodsReceiptResponse update(@PathVariable Long id,
                                       @Valid @RequestBody GoodsReceiptUpdateRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping("/{id}/confirm")
    public GoodsReceiptResponse confirm(@PathVariable Long id,
                                        @Valid @RequestBody GoodsReceiptConfirmRequest request) {
        return service.confirm(id, request);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping("/{id}/cancel")
    public GoodsReceiptResponse cancel(@PathVariable Long id,
                                       @Valid @RequestBody GoodsReceiptDecisionRequest request) {
        return service.cancel(id, request);
    }
}
