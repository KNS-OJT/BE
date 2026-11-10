package com.p03.inventory.po;

import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.security.Authz;
import com.p03.inventory.po.dto.PoCreateRequest;
import com.p03.inventory.po.dto.PoResponse;
import com.p03.inventory.po.dto.PoUpdateRequest;
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

/**
 * IN08. Purchasing lập / sửa / phát hành PO. Xem: Purchasing, Manager (tất cả), Warehouse (PO chờ nhận).
 * Requester không có quyền (403). Không có endpoint sửa status: chỉ chuyển trạng thái qua /order.
 */
@RestController
@RequestMapping("/api/po")
public class PoController {

    private final PoService poService;

    public PoController(PoService poService) {
        this.poService = poService;
    }

    @PreAuthorize(Authz.PURCHASING)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PoResponse create(@Valid @RequestBody PoCreateRequest request) {
        return poService.create(request);
    }

    /** GET /api/po?status=&page=&size= */
    @PreAuthorize(Authz.WAREHOUSE_PURCHASING_MANAGER)
    @GetMapping
    public PageResponse<PoResponse> list(@RequestParam(required = false) PoStatus status,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return poService.list(status, Paging.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @PreAuthorize(Authz.WAREHOUSE_PURCHASING_MANAGER)
    @GetMapping("/{id}")
    public PoResponse get(@PathVariable Long id) {
        return poService.get(id);
    }

    @PreAuthorize(Authz.PURCHASING)
    @PutMapping("/{id}")
    public PoResponse update(@PathVariable Long id, @Valid @RequestBody PoUpdateRequest request) {
        return poService.update(id, request);
    }

    @PreAuthorize(Authz.PURCHASING)
    @PostMapping("/{id}/order")
    public PoResponse order(@PathVariable Long id) {
        return poService.order(id);
    }
}
