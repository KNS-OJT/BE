package com.p03.inventory.pr;

import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.security.Authz;
import com.p03.inventory.pr.dto.PrCancelRequest;
import com.p03.inventory.pr.dto.PrCreateRequest;
import com.p03.inventory.pr.dto.PrDetailResponse;
import com.p03.inventory.pr.dto.PrRejectRequest;
import com.p03.inventory.pr.dto.PrSummaryResponse;
import com.p03.inventory.pr.dto.PrUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API phiếu yêu cầu mua hàng – /api/pr (IN06, IN07).
 * <p>
 * Quy tắc phân quyền (lớp 1 – RBAC):
 * <ul>
 *   <li>GET list/detail: mọi vai trò đã đăng nhập (Requester chỉ thấy PR mình tại lớp 2).</li>
 *   <li>POST (tạo Draft), PUT (sửa Draft), submit, cancel: mọi vai trò (chủ sở hữu kiểm tại lớp 2).</li>
 *   <li>approve, reject: chỉ MANAGER.</li>
 * </ul>
 * Lớp 2 (owner check) và lớp 3 (trạng thái, SoD) kiểm tra trong service.
 */
@RestController
@RequestMapping("/api/pr")
public class PrController {

    private final PrService prService;

    public PrController(PrService prService) {
        this.prService = prService;
    }

    // ───────────────────── QUERY ─────────────────────

    /** GET /api/pr?status=&q=&page=&size= */
    @PreAuthorize(Authz.AUTHENTICATED)
    @GetMapping
    public PageResponse<PrSummaryResponse> list(@RequestParam(required = false) PrStatus status,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return prService.list(status, q, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** GET /api/pr/{id} */
    @PreAuthorize(Authz.AUTHENTICATED)
    @GetMapping("/{id}")
    public PrDetailResponse get(@PathVariable Long id) {
        return prService.get(id);
    }

    // ───────────────────── COMMAND ─────────────────────

    /** POST /api/pr – Tạo Draft mới. */
    @PreAuthorize(Authz.AUTHENTICATED)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrDetailResponse create(@Valid @RequestBody PrCreateRequest request) {
        return prService.create(request);
    }

    /** PUT /api/pr/{id} – Cập nhật nội dung Draft (BR-11). */
    @PreAuthorize(Authz.AUTHENTICATED)
    @PutMapping("/{id}")
    public PrDetailResponse update(@PathVariable Long id, @Valid @RequestBody PrUpdateRequest request) {
        return prService.update(id, request);
    }

    /** POST /api/pr/{id}/submit – DRAFT → SUBMITTED. */
    @PreAuthorize(Authz.AUTHENTICATED)
    @PostMapping("/{id}/submit")
    public PrDetailResponse submit(@PathVariable Long id) {
        return prService.submit(id);
    }

    /** POST /api/pr/{id}/approve – SUBMITTED → APPROVED (Manager only). */
    @PreAuthorize(Authz.MANAGER)
    @PostMapping("/{id}/approve")
    public PrDetailResponse approve(@PathVariable Long id) {
        return prService.approve(id);
    }

    /** POST /api/pr/{id}/reject – SUBMITTED → REJECTED (Manager only). */
    @PreAuthorize(Authz.MANAGER)
    @PostMapping("/{id}/reject")
    public PrDetailResponse reject(@PathVariable Long id, @Valid @RequestBody PrRejectRequest request) {
        return prService.reject(id, request);
    }

    /** POST /api/pr/{id}/cancel – DRAFT/SUBMITTED → CANCELED (chủ sở hữu). */
    @PreAuthorize(Authz.AUTHENTICATED)
    @PostMapping("/{id}/cancel")
    public PrDetailResponse cancel(@PathVariable Long id, @Valid @RequestBody PrCancelRequest request) {
        return prService.cancel(id, request);
    }
}

