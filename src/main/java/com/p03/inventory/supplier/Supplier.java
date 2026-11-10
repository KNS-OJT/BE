package com.p03.inventory.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Nhà cung cấp (IN03). Không xóa vật lý (trigger tg_suppliers_no_delete): ngừng hợp tác bằng active = false.
 * Nhà cung cấp ngừng hợp tác không được chọn cho PO mới và PO DRAFT không được phát hành với nhà cung cấp đó.
 */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    private Long id;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
