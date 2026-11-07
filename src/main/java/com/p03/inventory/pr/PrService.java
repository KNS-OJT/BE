package com.p03.inventory.pr;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.common.util.Text;
import com.p03.inventory.pr.dto.PrCancelRequest;
import com.p03.inventory.pr.dto.PrCreateRequest;
import com.p03.inventory.pr.dto.PrDetailResponse;
import com.p03.inventory.pr.dto.PrItemRequest;
import com.p03.inventory.pr.dto.PrRejectRequest;
import com.p03.inventory.pr.dto.PrSummaryResponse;
import com.p03.inventory.pr.dto.PrUpdateRequest;
import com.p03.inventory.product.Product;
import com.p03.inventory.product.ProductRepository;
import com.p03.inventory.user.User;
import com.p03.inventory.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nghiệp vụ phiếu yêu cầu mua hàng (IN06, IN07). Bám sát CONTRIBUTING.md:
 * <ul>
 *   <li>Người thực hiện: {@code CurrentUser.id()} – KHÔNG nhận từ body.</li>
 *   <li>Transaction: {@code @Transactional} + manager tự set app.current_user_id.</li>
 *   <li>Khóa dòng: {@code findByIdForUpdate} (SELECT ... FOR UPDATE) khi đổi trạng thái.</li>
 *   <li>Kiểm tra quyền sở hữu: Requester chỉ thấy/sửa PR của mình (BR-20 → 404).</li>
 *   <li>SoD: Manager không tự duyệt PR do chính mình tạo (BR-08, IA-IN01 → 403).</li>
 *   <li>Trigger DB: backup layer – kiểm tra thêm ở level database.</li>
 * </ul>
 */
@Service
public class PrService {

    private final PurchaseRequestRepository prRepo;
    private final PurchaseRequestItemRepository prItemRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;

    public PrService(PurchaseRequestRepository prRepo,
                     PurchaseRequestItemRepository prItemRepo,
                     ProductRepository productRepo,
                     UserRepository userRepo) {
        this.prRepo = prRepo;
        this.prItemRepo = prItemRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
    }

    // ───────────────────────────────────────────────────────────────────────
    // QUERY
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Danh sách PR (IN07). Requester chỉ thấy PR của mình; Manager/Staff thấy tất cả.
     */
    @Transactional(readOnly = true)
    public PageResponse<PrSummaryResponse> list(PrStatus status, String q, Pageable pageable) {
        Long requesterId = CurrentUser.is(Role.REQUESTER) ? CurrentUser.id() : null;
        String pattern = q == null ? null : Text.likePattern(q);
        return PageResponse.of(
                prRepo.search(requesterId, status, pattern, pageable),
                PrSummaryResponse::from
        );
    }

    /**
     * Chi tiết PR kèm danh sách dòng hàng. Requester chỉ xem PR của mình (BR-20 → 404).
     */
    @Transactional(readOnly = true)
    public PrDetailResponse get(Long id) {
        PurchaseRequest pr = prRepo.findByIdWithUsers(id)
                .orElseThrow(ApiException::notFound);
        assertVisibility(pr);
        List<PurchaseRequestItem> items = prItemRepo.findByPurchaseRequestId(id);
        return PrDetailResponse.from(pr, items);
    }

    // ───────────────────────────────────────────────────────────────────────
    // COMMAND: Tạo Draft
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Tạo phiếu yêu cầu mua ở trạng thái DRAFT (IN06). Requester lấy từ JWT.
     */
    @Transactional
    public PrDetailResponse create(PrCreateRequest req) {
        validateItemList(req.items());

        User requester = userRepo.getReferenceById(CurrentUser.id());

        PurchaseRequest pr = new PurchaseRequest();
        pr.setRequester(requester);
        pr.setReason(req.reason().trim());
        pr.setStatus(PrStatus.DRAFT);
        pr = prRepo.saveAndFlush(pr);

        List<PurchaseRequestItem> items = saveItems(pr, req.items());
        return PrDetailResponse.from(pr, items);
    }

    // ───────────────────────────────────────────────────────────────────────
    // COMMAND: Cập nhật Draft
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Cập nhật nội dung phiếu DRAFT (BR-11). Chỉ chủ sở hữu mới sửa được.
     * Thay thế toàn bộ reason + danh sách dòng hàng.
     */
    @Transactional
    public PrDetailResponse update(Long id, PrUpdateRequest req) {
        PurchaseRequest pr = prRepo.findByIdForUpdate(id)
                .orElseThrow(ApiException::notFound);
        assertOwner(pr);
        assertStatus(pr, PrStatus.DRAFT, "Chỉ phiếu DRAFT mới cập nhật được nội dung");

        validateItemList(req.items());

        pr.setReason(req.reason().trim());
        prRepo.saveAndFlush(pr);

        // Xoá items cũ, thêm items mới
        prItemRepo.deleteAllByPurchaseRequestId(id);
        prItemRepo.flush();
        List<PurchaseRequestItem> items = saveItems(pr, req.items());
        return PrDetailResponse.from(pr, items);
    }

    // ───────────────────────────────────────────────────────────────────────
    // COMMAND: Submit (DRAFT → SUBMITTED)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Submit phiếu để Manager duyệt (DRAFT → SUBMITTED). Chỉ chủ sở hữu submit.
     * BR-12: phải có ít nhất 1 dòng hàng.
     */
    @Transactional
    public PrDetailResponse submit(Long id) {
        PurchaseRequest pr = prRepo.findByIdForUpdate(id)
                .orElseThrow(ApiException::notFound);
        assertOwner(pr);
        assertStatus(pr, PrStatus.DRAFT, "Chỉ phiếu DRAFT mới submit được");

        if (!prItemRepo.existsByPurchaseRequestId(id)) {
            throw ApiException.validation("Phiếu yêu cầu cần có ít nhất một dòng hàng trước khi submit (BR-12)");
        }

        pr.setStatus(PrStatus.SUBMITTED);
        // submitted_at do trigger DB tự set = now()
        prRepo.saveAndFlush(pr);

        List<PurchaseRequestItem> items = prItemRepo.findByPurchaseRequestId(id);
        return PrDetailResponse.from(pr, items);
    }

    // ───────────────────────────────────────────────────────────────────────
    // COMMAND: Approve (SUBMITTED → APPROVED)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Manager duyệt phiếu (SUBMITTED → APPROVED).
     * SoD (BR-08, IA-IN01): Manager không tự duyệt PR do chính mình tạo.
     */
    @Transactional
    public PrDetailResponse approve(Long id) {
        PurchaseRequest pr = prRepo.findByIdForUpdate(id)
                .orElseThrow(ApiException::notFound);
        assertStatus(pr, PrStatus.SUBMITTED, "Chỉ phiếu SUBMITTED mới duyệt được");
        assertNotSelfApproval(pr);

        User reviewer = userRepo.getReferenceById(CurrentUser.id());
        pr.setStatus(PrStatus.APPROVED);
        pr.setReviewer(reviewer);
        // reviewed_at do trigger DB tự set = now()
        prRepo.saveAndFlush(pr);

        List<PurchaseRequestItem> items = prItemRepo.findByPurchaseRequestId(id);
        return PrDetailResponse.from(pr, items);
    }

    // ───────────────────────────────────────────────────────────────────────
    // COMMAND: Reject (SUBMITTED → REJECTED)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Manager từ chối phiếu (SUBMITTED → REJECTED).
     * BR-10: lý do từ chối bắt buộc. SoD: không tự từ chối PR mình tạo.
     */
    @Transactional
    public PrDetailResponse reject(Long id, PrRejectRequest req) {
        PurchaseRequest pr = prRepo.findByIdForUpdate(id)
                .orElseThrow(ApiException::notFound);
        assertStatus(pr, PrStatus.SUBMITTED, "Chỉ phiếu SUBMITTED mới từ chối được");
        assertNotSelfApproval(pr);

        User reviewer = userRepo.getReferenceById(CurrentUser.id());
        pr.setStatus(PrStatus.REJECTED);
        pr.setReviewer(reviewer);
        pr.setReviewReason(req.reason().trim());
        // reviewed_at do trigger DB tự set = now()
        prRepo.saveAndFlush(pr);

        List<PurchaseRequestItem> items = prItemRepo.findByPurchaseRequestId(id);
        return PrDetailResponse.from(pr, items);
    }

    // ───────────────────────────────────────────────────────────────────────
    // COMMAND: Cancel (DRAFT/SUBMITTED → CANCELED)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Requester huỷ phiếu (DRAFT/SUBMITTED → CANCELED). Chỉ chủ sở hữu huỷ được.
     * Lý do huỷ bắt buộc (ck_pr_cancel_reason).
     */
    @Transactional
    public PrDetailResponse cancel(Long id, PrCancelRequest req) {
        PurchaseRequest pr = prRepo.findByIdForUpdate(id)
                .orElseThrow(ApiException::notFound);
        assertOwner(pr);

        if (pr.getStatus() != PrStatus.DRAFT && pr.getStatus() != PrStatus.SUBMITTED) {
            throw ApiException.invalidState("Chỉ phiếu DRAFT hoặc SUBMITTED mới huỷ được");
        }

        pr.setStatus(PrStatus.CANCELED);
        pr.setCancelReason(req.reason().trim());
        prRepo.saveAndFlush(pr);

        List<PurchaseRequestItem> items = prItemRepo.findByPurchaseRequestId(id);
        return PrDetailResponse.from(pr, items);
    }

    // ───────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ───────────────────────────────────────────────────────────────────────

    /**
     * BR-20: Requester chỉ thấy PR của mình. Nếu không phải chủ sở hữu và là Requester → 404 (không lộ sự tồn tại).
     */
    private void assertVisibility(PurchaseRequest pr) {
        if (CurrentUser.is(Role.REQUESTER) && !pr.getRequester().getId().equals(CurrentUser.id())) {
            throw ApiException.notOwner("PR", pr.getId());
        }
    }

    /**
     * Kiểm tra chủ sở hữu: thao tác sửa/submit/cancel chỉ dành cho requester tạo PR.
     */
    private void assertOwner(PurchaseRequest pr) {
        if (!pr.getRequester().getId().equals(CurrentUser.id())) {
            throw ApiException.notOwner("PR", pr.getId());
        }
    }

    /**
     * Kiểm tra trạng thái đúng, nếu sai → 409.
     */
    private static void assertStatus(PurchaseRequest pr, PrStatus expected, String message) {
        if (pr.getStatus() != expected) {
            throw ApiException.invalidState(message + " (hiện tại: " + pr.getStatus() + ")");
        }
    }

    /**
     * SoD (BR-08, IA-IN01): người tạo PR không được tự duyệt/từ chối.
     */
    private void assertNotSelfApproval(PurchaseRequest pr) {
        if (pr.getRequester().getId().equals(CurrentUser.id())) {
            throw ApiException.sodViolation("PR", pr.getId());
        }
    }

    /**
     * Validate danh sách dòng hàng: không trùng productId, sản phẩm phải tồn tại và đang hoạt động (BR-12).
     */
    private void validateItemList(List<PrItemRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (PrItemRequest item : items) {
            if (!seen.add(item.productId())) {
                throw ApiException.validation("Sản phẩm trùng lặp trong danh sách: productId = " + item.productId());
            }
        }
        // Kiểm tra tất cả product tồn tại và đang hoạt động
        for (PrItemRequest item : items) {
            Product product = productRepo.findById(item.productId())
                    .orElseThrow(() -> ApiException.validation("Sản phẩm không tồn tại: productId = " + item.productId()));
            if (!product.isActive()) {
                throw ApiException.validation("Sản phẩm đã ngừng hoạt động: " + product.getSku() + " (BR-12)");
            }
        }
    }

    /**
     * Lưu danh sách dòng hàng cho PR. Trả về list đã lưu (kèm entity Product cho response).
     */
    private List<PurchaseRequestItem> saveItems(PurchaseRequest pr, List<PrItemRequest> items) {
        List<PurchaseRequestItem> entities = items.stream().map(req -> {
            PurchaseRequestItem item = new PurchaseRequestItem();
            item.setPurchaseRequest(pr);
            item.setProduct(productRepo.getReferenceById(req.productId()));
            item.setQuantity(req.quantity());
            item.setNote(req.note() != null ? req.note().trim() : null);
            return item;
        }).toList();
        prItemRepo.saveAllAndFlush(entities);
        // Reload kèm product để response có đầy đủ thông tin
        return prItemRepo.findByPurchaseRequestId(pr.getId());
    }
}

