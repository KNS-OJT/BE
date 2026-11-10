package com.p03.inventory.product;

import com.p03.inventory.category.Category;
import com.p03.inventory.category.CategoryRepository;
import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.common.util.Text;
import com.p03.inventory.product.dto.MinStockRequest;
import com.p03.inventory.product.dto.MinStockResponse;
import com.p03.inventory.product.dto.ProductCreateRequest;
import com.p03.inventory.product.dto.ProductResponse;
import com.p03.inventory.product.dto.ProductUpdateRequest;
import com.p03.inventory.stock.StockBalanceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sản phẩm (IN02, IN05, IN15).
 * <ul>
 *   <li>BR-01: SKU duy nhất kể cả sản phẩm ngừng; không đổi SKU khi đã dùng trong chứng từ.</li>
 *   <li>Không xóa: ngừng kinh doanh bằng active = false.</li>
 *   <li>Tạo sản phẩm → trigger tự tạo dòng stock_balances = 0. Backend không bao giờ ghi số tồn.</li>
 *   <li>Đổi minimum_stock: database tự ghi audit giá trị cũ/mới (trigger trên stock_balances).</li>
 * </ul>
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final StockBalanceRepository stockBalances;

    public ProductService(ProductRepository products, CategoryRepository categories,
                          StockBalanceRepository stockBalances) {
        this.products = products;
        this.categories = categories;
        this.stockBalances = stockBalances;
    }

    /** Requester/Purchasing chỉ thấy sản phẩm đang hoạt động; Warehouse/Manager thấy cả sản phẩm đã ngừng. */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, Long categoryId, Boolean activeOnly, Pageable pageable) {
        boolean onlyActive = !canManage() || Boolean.TRUE.equals(activeOnly);
        return PageResponse.of(products.search(Text.likePattern(q), categoryId, onlyActive, pageable),
                ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        Product p = load(id);
        if (!p.isActive() && !canManage()) {
            throw ApiException.notFound();
        }
        return ProductResponse.from(p);
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest req) {
        if (products.existsBySku(req.sku())) {
            throw ApiException.duplicate("SKU đã tồn tại (kể cả sản phẩm đã ngừng hoạt động)");
        }
        Product p = new Product();
        p.setSku(req.sku());
        p.setName(req.name().trim());
        p.setCategory(activeCategory(req.categoryId()));
        p.setUnit(req.unit().trim());
        p.setActive(true);
        return ProductResponse.from(products.saveAndFlush(p));
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest req) {
        Product p = load(id);
        if (!p.getSku().equals(req.sku())) {
            if (products.isUsedInDocuments(id)) {
                throw ApiException.invalidState("SKU đã dùng trong chứng từ nên không thể thay đổi (BR-01)");
            }
            if (products.existsBySku(req.sku())) {
                throw ApiException.duplicate("SKU đã tồn tại (kể cả sản phẩm đã ngừng hoạt động)");
            }
            p.setSku(req.sku());
        }
        if (!p.getCategory().getId().equals(req.categoryId())) {
            p.setCategory(activeCategory(req.categoryId()));
        }
        p.setName(req.name().trim());
        p.setUnit(req.unit().trim());
        p.setActive(req.active());
        return ProductResponse.from(products.saveAndFlush(p));
    }

    @Transactional
    public MinStockResponse updateMinimumStock(Long id, MinStockRequest req) {
        Product p = load(id);
        if (!p.isActive()) {
            throw ApiException.invalidState("Sản phẩm đã ngừng hoạt động");
        }
        stockBalances.updateMinimumStock(id, req.minimumStock());
        return new MinStockResponse(p.getId(), p.getSku(), req.minimumStock());
    }

    private Product load(Long id) {
        return products.findById(id).orElseThrow(ApiException::notFound);
    }

    private Category activeCategory(Long categoryId) {
        Category c = categories.findById(categoryId)
                .orElseThrow(() -> ApiException.validation("Nhóm hàng không tồn tại"));
        if (!c.isActive()) {
            throw ApiException.validation("Nhóm hàng đã ngừng sử dụng");
        }
        return c;
    }

    private static boolean canManage() {
        return CurrentUser.is(Role.WAREHOUSE_STAFF) || CurrentUser.is(Role.MANAGER);
    }
}
