package com.p03.inventory.stockissue;
import com.p03.inventory.common.paging.*;
import com.p03.inventory.common.security.Authz;
import com.p03.inventory.stockissue.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @RequestMapping("/api/stock-issues")
@Tag(name="Stock Issue", description="Tạo yêu cầu xuất, xác nhận hoặc từ chối; tồn chỉ đổi khi xác nhận")
public class StockIssueController {
    private static final String VIEW_ROLES="hasAnyRole('REQUESTER','WAREHOUSE_STAFF','PURCHASING_STAFF','MANAGER')";
    private final StockIssueService service;
    public StockIssueController(StockIssueService service) { this.service=service; }
    @PreAuthorize(VIEW_ROLES) @GetMapping
    public PageResponse<StockIssueResponse> list(@RequestParam(required=false) Integer page,
                                                @RequestParam(required=false) Integer size,
                                                @RequestParam(required=false) StockIssueStatus status) {
        return service.list(status, Paging.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
    }
    @PreAuthorize(VIEW_ROLES) @GetMapping("/{id}")
    public StockIssueResponse get(@PathVariable Long id) { return service.get(id); }
    @PreAuthorize(Authz.AUTHENTICATED) @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StockIssueResponse create(@Valid @RequestBody StockIssueCreateRequest body) { return service.create(body); }
    @PreAuthorize(Authz.AUTHENTICATED) @PutMapping("/{id}")
    public StockIssueResponse update(@PathVariable Long id, @Valid @RequestBody StockIssueUpdateRequest body) {
        return service.update(id,body);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping("/{id}/confirm")
    public StockIssueResponse confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @PreAuthorize(Authz.WAREHOUSE)
    @PostMapping("/{id}/reject")
    public StockIssueResponse reject(@PathVariable Long id,
                                     @Valid @RequestBody StockIssueDecisionRequest request) {
        return service.reject(id, request);
    }

    @PreAuthorize(Authz.AUTHENTICATED)
    @PostMapping("/{id}/cancel")
    public StockIssueResponse cancel(@PathVariable Long id,
                                     @Valid @RequestBody StockIssueDecisionRequest request) {
        return service.cancel(id, request);
    }
}
