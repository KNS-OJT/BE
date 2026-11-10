package com.p03.inventory.stockissue;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
@Entity @Table(name="stock_issue_items") @Getter @Setter
public class StockIssueItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="stock_issue_item_id") private Long id;
    @Column(name="stock_issue_id", nullable=false, updatable=false) private Long stockIssueId;
    @Column(name="product_id", nullable=false) private Long productId;
    @Column(name="quantity", nullable=false, precision=14, scale=3) private BigDecimal quantity;
}
