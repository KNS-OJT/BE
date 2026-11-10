package com.p03.inventory.supplier;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.util.Text;
import com.p03.inventory.supplier.dto.SupplierCreateRequest;
import com.p03.inventory.supplier.dto.SupplierResponse;
import com.p03.inventory.supplier.dto.SupplierUpdateRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhà cung cấp (IN03).
 * <ul>
 *   <li>Không có xóa: ngừng hợp tác bằng active = false (DB chặn DELETE – BR-16).</li>
 *   <li>Trường tùy chọn rỗng/khoảng trắng lưu NULL; email/điện thoại đã validate ở DTO, DB CHECK email chặn lại lần nữa.</li>
 * </ul>
 */
@Service
public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> search(String q, Boolean active, Pageable pageable) {
        return PageResponse.of(suppliers.search(Text.likePattern(q), active, pageable), SupplierResponse::from);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        return SupplierResponse.from(load(id));
    }

    @Transactional
    public SupplierResponse create(SupplierCreateRequest req) {
        Supplier s = new Supplier();
        s.setName(req.supplierName().trim());
        s.setContactPerson(Text.trimToNull(req.contactPerson()));
        s.setPhone(Text.trimToNull(req.phone()));
        s.setEmail(Text.trimToNull(req.email()));
        s.setAddress(Text.trimToNull(req.address()));
        s.setActive(true);
        return SupplierResponse.from(suppliers.saveAndFlush(s));
    }

    /** active = false: ngừng hợp tác (không chọn được cho PO mới); active = true: hợp tác lại. */
    @Transactional
    public SupplierResponse update(Long id, SupplierUpdateRequest req) {
        Supplier s = load(id);
        s.setName(req.supplierName().trim());
        s.setContactPerson(Text.trimToNull(req.contactPerson()));
        s.setPhone(Text.trimToNull(req.phone()));
        s.setEmail(Text.trimToNull(req.email()));
        s.setAddress(Text.trimToNull(req.address()));
        s.setActive(req.active());
        return SupplierResponse.from(suppliers.saveAndFlush(s));
    }

    private Supplier load(Long id) {
        return suppliers.findById(id).orElseThrow(ApiException::notFound);
    }
}
