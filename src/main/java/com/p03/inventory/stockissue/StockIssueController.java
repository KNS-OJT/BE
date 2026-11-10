package com.p03.inventory.stockissue;
import com.p03.inventory.common.paging.*;
import com.p03.inventory.stockissue.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @RequestMapping("/api/stock-issues")
@Tag(name="Stock Issue", description="Draft endpoints; submit workflow requires a database migration")
public class StockIssueController {
    private static final String SI_ROLES="hasAnyRole('REQUESTER','WAREHOUSE_STAFF','MANAGER')";
    private final StockIssueService service;
    public StockIssueController(StockIssueService service) { this.service=service; }
    @PreAuthorize(SI_ROLES) @GetMapping
    public PageResponse<StockIssueResponse> list(@RequestParam(required=false) Integer page,
                                                @RequestParam(required=false) Integer size) {
        return service.list(Paging.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
    }
    @PreAuthorize(SI_ROLES) @GetMapping("/{id}")
    public StockIssueResponse get(@PathVariable Long id) { return service.get(id); }
    @PreAuthorize(SI_ROLES) @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StockIssueResponse create(@Valid @RequestBody StockIssueCreateRequest body) { return service.create(body); }
    @PreAuthorize(SI_ROLES) @PutMapping("/{id}")
    public StockIssueResponse update(@PathVariable Long id, @Valid @RequestBody StockIssueUpdateRequest body) {
        return service.update(id,body);
    }
}
