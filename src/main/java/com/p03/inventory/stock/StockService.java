package com.p03.inventory.stock;

import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.stock.dto.StockResponse;
import com.p03.inventory.stock.dto.StockTransactionResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final StockBalanceRepository stockBalances;
    private final StockTransactionRepository stockTransactions;

    public StockService(
        StockBalanceRepository stockBalances,
        StockTransactionRepository stockTransactions
    ) {
        this.stockBalances = stockBalances;
        this.stockTransactions = stockTransactions;
    }

    /**
     * Lấy danh sách sản phẩm có số lượng tồn thấp hơn mức tồn tối thiểu.
     */
    @Transactional(readOnly = true)
    public PageResponse<StockResponse> getLowStock(Pageable pageable) {
        return PageResponse.of(
            stockBalances.findLowStock(pageable),
            StockResponse::from
        );
    }

    /**
     * Tìm kiếm tồn kho theo SKU hoặc tên sản phẩm.
     */
    @Transactional(readOnly = true)
    public PageResponse<StockResponse> search(
        String pattern,
        Pageable pageable
    ) {
        return PageResponse.of(
            stockBalances.search(pattern, pageable),
            StockResponse::from
        );
    }

    /**
     * Lấy lịch sử giao dịch kho có lọc và phân trang.
     */
    @Transactional(readOnly = true)
    public PageResponse<StockTransactionResponse> getTransactions(
        Long productId,
        StockTransactionType type,
        String pattern,
        Pageable pageable
    ) {
        return PageResponse.of(
            stockTransactions.search(productId, type, pattern, pageable),
            StockTransactionResponse::from
        );
    }
}
