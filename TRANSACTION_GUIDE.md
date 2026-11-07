# Hướng dẫn Transaction cho Backend P03 (dùng chung cả 6 nhánh)

> **Tác giả:** Lê Việt Anh — phụ trách PR module  
> **Mục đích:** Giải thích cách transaction hoạt động trong project để 5 thành viên còn lại áp dụng **đúng và giống nhau**.  
> **Đọc trước:** `CONTRIBUTING.md`, `db/README.md`, `db/init/01_schema.sql` (mục "HOW THE BACKEND MUST CALL THE DATABASE").

---

## 1. Tóm tắt: Backend chỉ cần `@Transactional`

Anh Minh đã xây sẵn `ActorAwareJpaTransactionManager` trong `common/tx/`. Khi một method có `@Transactional` được gọi, transaction manager **tự động** chạy:

```sql
SET LOCAL app.current_user_id = '<userId từ JWT>';
SET LOCAL app.request_id      = '<requestId>';
SET LOCAL app.client_ip       = '<client IP>';
```

Vì dùng `SET LOCAL`, các giá trị này **chỉ sống trong transaction hiện tại** — connection trả về pool sạch, không lẫn user.

**→ Các nhánh KHÔNG tự gọi `set_config`, KHÔNG viết TxRunner riêng.**

---

## 2. Quy tắc áp dụng

### 2.1. Người thực hiện — luôn từ JWT

```java
// ✅ ĐÚNG: lấy từ JWT
Long me = CurrentUser.id();
pr.setReviewer(userRepo.getReferenceById(me));

// ❌ SAI: nhận từ body client gửi lên
pr.setReviewer(userRepo.getReferenceById(request.getReviewerId()));
```

DTO request **KHÔNG ĐƯỢC** có các trường: `requesterId`, `createdBy`, `reviewedBy`, `confirmedBy`, `status`, `systemQuantity`.  
Jackson sẽ trả `400 UNKNOWN_FIELD` nếu client gửi trường lạ (đã bật `fail-on-unknown-properties=true`).

### 2.2. `@Transactional` đặt ở đâu

```java
@Service
public class PrService {

    // Thao tác GHI: @Transactional (mặc định REQUIRED, readWrite)
    @Transactional
    public PrDetailResponse approve(Long id) { ... }

    // Thao tác ĐỌC: @Transactional(readOnly = true) — tối ưu performance
    @Transactional(readOnly = true)
    public PrDetailResponse get(Long id) { ... }
}
```

- Đặt ở **service**, KHÔNG đặt ở controller hay repository.
- Mỗi method = 1 transaction. Không lồng `@Transactional` trong `@Transactional` (trừ `SecurityEventLogger` dùng `REQUIRES_NEW` nhưng đó là common, không phải việc của các nhánh).

### 2.3. Khóa dòng với `FOR UPDATE`

Khi **đổi trạng thái** chứng từ, PHẢI khóa dòng trước để tránh race condition (hai người cùng approve/confirm):

```java
// Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select pr from PurchaseRequest pr where pr.id = :id")
Optional<PurchaseRequest> findByIdForUpdate(@Param("id") Long id);

// Service
@Transactional
public PrDetailResponse approve(Long id) {
    PurchaseRequest pr = prRepo.findByIdForUpdate(id)        // ← khóa dòng
            .orElseThrow(ApiException::notFound);
    assertStatus(pr, PrStatus.SUBMITTED, "...");              // ← kiểm tra trạng thái
    pr.setStatus(PrStatus.APPROVED);                          // ← đổi trạng thái
    pr.setReviewer(userRepo.getReferenceById(CurrentUser.id()));
    prRepo.saveAndFlush(pr);                                  // ← flush để trigger DB chạy ngay
    ...
}
```

**Khi nào cần FOR UPDATE:**
- Submit, approve, reject, cancel, confirm — bất kỳ thao tác đổi status
- Thao tác có nguy cơ 2 request đồng thời (vd. 2 Warehouse Staff cùng confirm 1 phiếu)

**Khi nào KHÔNG cần:**
- Đọc danh sách, xem chi tiết (dùng `readOnly = true`)
- Cập nhật Draft (trigger DB đã khóa dòng khi kiểm tra status = DRAFT)

### 2.4. `saveAndFlush` thay vì `save`

Dùng `saveAndFlush` để trigger DB chạy **ngay trong transaction**, lỗi sẽ bị bắt sớm:

```java
prRepo.saveAndFlush(pr);  // ✅ trigger chạy ngay, lỗi bắt được
prRepo.save(pr);          // ⚠️ trigger chạy lúc commit, lỗi khó xử lý
```

### 2.5. Không ghi stock, ledger, audit trực tiếp

```java
// ❌ TUYỆT ĐỐI KHÔNG
stockBalance.setQuantity(stockBalance.getQuantity().subtract(qty));
auditLogRepo.save(new AuditLog(...));

// ✅ ĐÚNG: chỉ đổi trạng thái chứng từ → trigger DB lo phần còn lại
goodsReceipt.setStatus(DocumentStatus.CONFIRMED);
goodsReceipt.setConfirmedBy(userRepo.getReferenceById(CurrentUser.id()));
grRepo.saveAndFlush(goodsReceipt);
// → trigger tg_goods_receipts_post tự: cập nhật stock_balances, ghi stock_transactions, ghi audit_logs
```

---

## 3. Mẫu đầy đủ — 4 lớp kiểm tra

Dưới đây là mẫu approve PR, áp dụng cho mọi thao tác đổi trạng thái:

```java
@Transactional
public PrDetailResponse approve(Long id) {
    // ── Lớp 1: RBAC ─── (đã kiểm ở Controller bằng @PreAuthorize(Authz.MANAGER))
    
    // ── Lớp 2: Khóa dòng + tìm đối tượng ───
    PurchaseRequest pr = prRepo.findByIdForUpdate(id)
            .orElseThrow(ApiException::notFound);      // 404 nếu không tồn tại

    // ── Lớp 2b: Owner check (nếu cần) ───
    // Ví dụ với Requester: nếu không phải chủ sở hữu → 404 (không lộ sự tồn tại)
    // if (!pr.getRequester().getId().equals(CurrentUser.id())) {
    //     throw ApiException.notOwner("PR", pr.getId());  // → 404 + audit NOT_OWNER
    // }

    // ── Lớp 3a: Trạng thái ───
    if (pr.getStatus() != PrStatus.SUBMITTED) {
        throw ApiException.invalidState("Chỉ phiếu SUBMITTED mới duyệt được");  // → 409
    }

    // ── Lớp 3b: SoD (Segregation of Duties) ───
    if (pr.getRequester().getId().equals(CurrentUser.id())) {
        throw ApiException.sodViolation("PR", pr.getId());  // → 403 + audit SOD_VIOLATION
    }

    // ── Thực hiện nghiệp vụ ───
    pr.setStatus(PrStatus.APPROVED);
    pr.setReviewer(userRepo.getReferenceById(CurrentUser.id()));
    // reviewed_at do trigger DB tự set = now()
    prRepo.saveAndFlush(pr);

    // ── Lớp 4: Database trigger kiểm tra lại tất cả (backup) ───
    // → trg_purchase_requests_workflow chạy tự động, không cần code gì thêm

    return PrDetailResponse.from(pr, prItemRepo.findByPurchaseRequestId(id));
}
```

---

## 4. Trường hợp đặc biệt: `ActorContext.runAs`

Chỉ dùng khi **KHÔNG có JWT** (vd. cập nhật failed_login_count lúc đăng nhập):

```java
// Ví dụ trong AuthService (module auth của Anh Minh)
ActorContext.runAs(user.getId(), () -> {
    userService.recordSuccessfulLogin(user.getId());  // transaction mở BÊN TRONG runAs
});
```

**Nghiệp vụ bình thường KHÔNG dùng `ActorContext`.** Transaction manager tự lấy từ `CurrentUser` (JWT).

---

## 5. Checklist trước khi commit

| # | Kiểm tra | Đúng |
|---|----------|------|
| 1 | Method ghi có `@Transactional`? Method đọc có `readOnly = true`? | ☐ |
| 2 | Người thực hiện lấy từ `CurrentUser.id()`, KHÔNG từ body? | ☐ |
| 3 | Đổi trạng thái dùng `findByIdForUpdate` (FOR UPDATE)? | ☐ |
| 4 | Dùng `saveAndFlush` để trigger chạy ngay? | ☐ |
| 5 | DTO request KHÔNG có trường `status`, `createdBy`, `reviewedBy`, `confirmedBy`? | ☐ |
| 6 | KHÔNG ghi trực tiếp `stock_balances`, `stock_transactions`, `audit_logs`? | ☐ |
| 7 | Kiểm tra owner (nếu có) trả 404 bằng `ApiException.notOwner()`? | ☐ |
| 8 | Kiểm tra SoD (nếu có) trả 403 bằng `ApiException.sodViolation()`? | ☐ |
| 9 | Controller có `@PreAuthorize(Authz.…)` trên MỌI endpoint? | ☐ |
| 10 | Lỗi dùng `ApiException` factory — KHÔNG tự thêm ErrorCode mới? | ☐ |

---

## 6. Áp dụng cho từng module

| Module | Owner | FOR UPDATE khi | SoD |
|--------|-------|---------------|-----|
| PR | Việt Anh | submit, approve, reject, cancel | Requester ≠ reviewer |
| PO | Trang | phát hành (ORDERED), cancel | — |
| GR | Hoàng Anh | confirm, cancel | — |
| SI | Anh Khoa | confirm, cancel | Requester ≠ confirmer |
| SA | Hoàng Anh | confirm, cancel | Creator ≠ confirmer |
| Stock/Dashboard | Trung Hiếu | — (chỉ đọc) | — |

> **Câu hỏi?** Liên hệ Việt Anh hoặc Anh Minh. Đừng tự viết cơ chế transaction riêng.

