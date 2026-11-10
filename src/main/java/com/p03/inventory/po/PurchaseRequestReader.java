package com.p03.inventory.po;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Đọc CHỈ-ĐỌC dữ liệu PR cho module PO, vì entity PR thuộc nhánh khác (Lê Việt Anh). Không ghi gì vào bảng PR.
 * <ul>
 *   <li>Chỉ SELECT đúng các cột cần: status, purchase_request_item_id, product_id, quantity.</li>
 *   <li>Mọi giá trị đi qua tham số bind, không nối chuỗi SQL.</li>
 *   <li>Chạy trong transaction của service gọi nó; p03_app có SELECT trên mọi bảng (02_roles.sql).</li>
 * </ul>
 * Đây chỉ là kiểm tra sớm để trả lỗi rõ ràng: trigger trg_purchase_orders_workflow (PR APPROVED, khóa FOR SHARE)
 * và trg_purchase_order_items_guard (dòng thuộc PR, số lượng ≤ đã duyệt) kiểm tra lại khi ghi.
 * Khi module PR có entity/repository, thay phần cài đặt của class này.
 */
@Repository
public class PurchaseRequestReader {

    /** Dòng PR: id, sản phẩm, số lượng đã duyệt. */
    public record PrItem(Long id, Long productId, BigDecimal quantity) {
    }

    @PersistenceContext
    private EntityManager em;

    /** Trạng thái PR (tên enum purchase_request_status) hoặc rỗng nếu PR không tồn tại. */
    public Optional<String> findStatus(Long prId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT CAST(status AS text) FROM purchase_requests WHERE purchase_request_id = :prId")
                .setParameter("prId", prId)
                .getResultList();
        return rows.stream().findFirst().map(Object::toString);
    }

    /** Các dòng của PR, theo purchase_request_item_id. */
    public Map<Long, PrItem> findItems(Long prId) {
        List<?> rows = em.createNativeQuery("""
                        SELECT purchase_request_item_id, product_id, quantity
                          FROM purchase_request_items
                         WHERE purchase_request_id = :prId
                         ORDER BY purchase_request_item_id
                        """)
                .setParameter("prId", prId)
                .getResultList();
        Map<Long, PrItem> items = new LinkedHashMap<>();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            PrItem item = new PrItem(toLong(r[0]), toLong(r[1]), (BigDecimal) r[2]);
            items.put(item.id(), item);
        }
        return items;
    }

    /** product_id của các dòng PR – dùng để hiển thị PO. */
    public Map<Long, Long> findProductIds(Collection<Long> prItemIds) {
        Map<Long, Long> result = new HashMap<>();
        if (prItemIds.isEmpty()) {
            return result;
        }
        List<?> rows = em.createNativeQuery("""
                        SELECT purchase_request_item_id, product_id
                          FROM purchase_request_items
                         WHERE purchase_request_item_id IN (:prItemIds)
                        """)
                .setParameter("prItemIds", prItemIds)
                .getResultList();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            result.put(toLong(r[0]), toLong(r[1]));
        }
        return result;
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }
}
