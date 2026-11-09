package com.p03.inventory.stockissue;
import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.security.*;
import com.p03.inventory.product.ProductRepository;
import com.p03.inventory.stockissue.dto.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class StockIssueService {
    private final StockIssueRepository issues;
    private final StockIssueItemRepository lines;
    private final ProductRepository products;
    public StockIssueService(StockIssueRepository issues, StockIssueItemRepository lines,
                             ProductRepository products) {
        this.issues=issues; this.lines=lines; this.products=products;
    }
    // Conservative initial visibility: all roles see only their OWN issues.
    // Warehouse review queue belongs to the separately agreed submit workflow.
    @Transactional(readOnly=true)
    public PageResponse<StockIssueResponse> list(Pageable pageable) {
        requireRole();
        return PageResponse.of(issues.findByRequestedBy(CurrentUser.id(), pageable), this::response);
    }
    @Transactional(readOnly=true)
    public StockIssueResponse get(Long id) {
        requireRole();
        StockIssue issue=issues.findById(id).orElseThrow(ApiException::notFound);
        requireOwner(issue);
        return response(issue);
    }
    @Transactional
    public StockIssueResponse create(StockIssueCreateRequest request) {
        requireRole();
        validateLines(request.items());
        StockIssue issue=new StockIssue();
        issue.setRequestedBy(CurrentUser.id());
        issue.setReason(request.reason().trim());
        issues.saveAndFlush(issue);
        insertLines(issue.getId(), request.items());
        return response(issue);
    }
    @Transactional
    public StockIssueResponse update(Long id, StockIssueUpdateRequest request) {
        requireRole();
        StockIssue issue=issues.lockById(id).orElseThrow(ApiException::notFound);
        requireOwner(issue);
        if(issue.getStatus()!=StockIssueStatus.DRAFT)
            throw ApiException.invalidStateAudited("Chỉ được sửa phiếu DRAFT", "SI", id);
        validateLines(request.items());
        // Flush deletes before inserts to avoid unique(issue, product) collisions.
        lines.deleteAll(lines.findByStockIssueIdOrderByIdAsc(id));
        lines.flush();
        issue.setReason(request.reason().trim());
        issues.saveAndFlush(issue);
        insertLines(id, request.items());
        return response(issue);
    }
    private void validateLines(List<StockIssueLineRequest> items) {
        Set<Long> seen=new HashSet<>();
        for(var item:items) {
            if(!seen.add(item.productId())) throw ApiException.validation("Sản phẩm bị trùng trong phiếu");
            var product=products.findById(item.productId())
                    .orElseThrow(() -> ApiException.validation("Sản phẩm không tồn tại"));
            if(!product.isActive()) throw ApiException.validation("Sản phẩm đã ngừng hoạt động");
        }
    }
    private void insertLines(Long id, List<StockIssueLineRequest> items) {
        for(var item:items) {
            StockIssueItem line=new StockIssueItem();
            line.setStockIssueId(id); line.setProductId(item.productId()); line.setQuantity(item.quantity());
            lines.save(line);
        }
        lines.flush();
    }
    private StockIssueResponse response(StockIssue issue) {
        var items=lines.findByStockIssueIdOrderByIdAsc(issue.getId()).stream()
                .map(l -> new StockIssueResponse.Line(l.getId(),l.getProductId(),l.getQuantity())).toList();
        return new StockIssueResponse(issue.getId(), issue.getCode(), issue.getRequestedBy(),
                issue.getConfirmedBy(),issue.getStatus(),issue.getRequestedAt(),issue.getConfirmedAt(),
                issue.getReason(),issue.getCancelReason(),items);
    }
    private static void requireOwner(StockIssue issue) {
        if(!CurrentUser.id().equals(issue.getRequestedBy())) throw ApiException.notOwner("SI",issue.getId());
    }
    private static void requireRole() {
        if(!CurrentUser.is(Role.REQUESTER) && !CurrentUser.is(Role.WAREHOUSE_STAFF) && !CurrentUser.is(Role.MANAGER))
            throw ApiException.forbiddenRole("SI",null);
    }
}
