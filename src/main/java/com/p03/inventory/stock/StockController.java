package com.p03.inventory.stock;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.util.Text;
import com.p03.inventory.stock.dto.StockResponse;
import com.p03.inventory.stock.dto.StockTransactionResponse;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.p03.inventory.common.security.Authz;

@RestController
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * Tìm kiếm danh sách tồn kho.
     */
    @PreAuthorize(Authz.AUTHENTICATED)
    @GetMapping("/api/stock")
    public PageResponse<StockResponse> search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
    ) {
        var pageable = Paging.of(
            page,
            size,
            Sort.by("productId").ascending()
        );

        return stockService.search(
            Text.likePattern(q),
            pageable
        );
    }

    /**
     * Lấy danh sách sản phẩm có tồn kho thấp.
     */
    @PreAuthorize(Authz.AUTHENTICATED)
    @GetMapping("/api/stock/low")
    public PageResponse<StockResponse> getLowStock(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
    ) {
        var pageable = Paging.of(
            page,
            size,
            Sort.by("productId").ascending()
        );

        return stockService.getLowStock(pageable);
    }

    /**
     * Lấy lịch sử giao dịch kho.
     */
    @PreAuthorize(Authz.AUTHENTICATED)
    @GetMapping("/api/stock-transactions")
    public PageResponse<StockTransactionResponse> getTransactions(
        @RequestParam(required = false) Long productId,
        @RequestParam(required = false) StockTransactionType type,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
    ) {
        var pageable = Paging.of(
            page,
            size,
            Sort.by("createdAt").descending()
                .and(Sort.by("id").descending())
        );

        return stockService.getTransactions(
            productId,
            type,
            Text.likePattern(q),
            pageable
        );
    }
}
