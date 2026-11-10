# Quy ước code backend P03 (bắt buộc cho cả 6 nhánh)

Khung chung nằm trong `com.p03.inventory.common`. Đọc file này trước khi code module của mình.

## 1. Nhánh & phân công

| Thành viên | Nhánh | Package | Endpoint |
|---|---|---|---|
| Nguyễn Anh Minh | `member1` | `auth`, `user`, `product`, `category`, `common` | `/api/auth`, `/api/users`, `/api/products`, `/api/categories` |
| Lê Việt Anh | `Vanh` | `pr` | `/api/pr/**` |
| Mai Thùy Trang | `feature/po-supplier-api` | `supplier`, `po` | `/api/suppliers/**`, `/api/po/**` |
| Nguyễn Đức Hoàng Anh | nhánh riêng | `goodsreceipt`, `stockadjustment` | `/api/goods-receipts/**`, `/api/stock-adjustments/**` |
| Nguyễn Trung Hiếu | nhánh riêng | `stock`, `dashboard` | `/api/stock`, `/api/stock/low`, `/api/stock-transactions`, `/api/dashboard` |
| Dương Quỳnh Anh Khoa | nhánh riêng | `stockissue` | `/api/stock-issues/**` |

- Luôn tạo nhánh từ `main` mới nhất; trước khi mở PR: `git pull origin main` vào nhánh mình, chạy `./mvnw test`.
- **Không sửa `common/`, `db/` hoặc entity của module khác trong nhánh của mình.** Cần đổi → báo Minh, làm PR riêng.

## 2. Phân quyền – 4 lớp (SDD mục 6)

1. **Route:** mọi handler phải có `@PreAuthorize(Authz.…)` (hoặc `@PermitAll`). Thiếu → **app không khởi động**.
2. **Đối tượng (BR-20):** Requester xem đối tượng của người khác → `throw ApiException.notOwner("PR", id)` (trả 404 + tự ghi audit).
3. **Trạng thái & SoD:** sai trạng thái → `ApiException.invalidState("…")`; tự duyệt / tự xác nhận → `ApiException.sodViolation("PR", id)`.
4. **Database:** trigger kiểm tra lại; lỗi DB được `DbErrorTranslator` tự đổi thành 403/409/400 – không cần bắt.

```java
@PreAuthorize(Authz.MANAGER)
@PostMapping("/{id}/approve")
public PrResponse approve(@PathVariable Long id) { return service.approve(id); }
```

## 3. Người thực hiện (quan trọng nhất)

- Người đang thao tác: `CurrentUser.id()`, `CurrentUser.is(Role.MANAGER)`. **Không bao giờ** nhận `createdBy`, `requesterId`,
  `reviewedBy`, `confirmedBy`, `status`, `systemQuantity` từ body.
- Chỉ cần `@Transactional` ở service. Transaction manager **tự** chạy `set_config('app.current_user_id', …)` và
  `app.client_ip` cho mọi transaction (đúng điểm 2 trong `db/README.md`). Không tự gọi set_config.
- Khi DB yêu cầu cột người thực hiện (vd. `confirmed_by`), gán `= CurrentUser.id()`; trigger sẽ so với actor (IA-IN07).
- **Không bao giờ sửa số tồn trực tiếp.** Chỉ đổi chứng từ sang `CONFIRMED`; trigger ghi ledger + cập nhật tồn.

## 4. DTO & validate

- Mỗi thao tác một `record` riêng trong `<module>/dto` (`XxxCreateRequest`, `XxxUpdateRequest`, `XxxResponse`).
- Body có trường lạ → 400 `UNKNOWN_FIELD` tự động. Validate bằng `jakarta.validation` (`@NotNull`, `@Positive`,
  `@Digits(integer = 11, fraction = 3)` cho số lượng numeric(14,3)).
- Không trả entity ra API. Không bao giờ trả `password_hash`.

## 5. Entity & enum PostgreSQL

Cột trạng thái là ENUM của PostgreSQL, giá trị VIẾT HOA (`DRAFT`, `SUBMITTED`…). Map như sau:

```java
@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Column(name = "status", nullable = false, columnDefinition = "purchase_request_status")
private PrStatus status;
```

- Khóa chính: `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- Mã chứng từ (`request_code`, `order_code`…), `created_at`: DB tự sinh → `@Column(insertable = false, updatable = false)` + `@Generated`.
- Tham chiếu module khác: dùng entity có sẵn (`User`, `Product`) ở chế độ **chỉ đọc**, hoặc chỉ giữ `Long id`.
- `ddl-auto=validate`: entity sai tên/kiểu cột thì app không khởi động – đó là tính năng, không phải lỗi.

## 6. Lỗi & phân trang

- Chỉ ném `ApiException` (xem các factory trong class). Mã lỗi cố định trong `ErrorCode` – không tự thêm.
- Danh sách: `Paging.of(page, size, Sort.by(...))` (page từ 0, size ≤ 100) và trả `PageResponse<T>`.
- Tìm kiếm không phân biệt hoa thường: `Text.likePattern(q)` + `lower(col) like :pattern escape '\'`.

## 7. Tài khoản demo (db/init/03_seed.sql)

Mật khẩu chung `Demo@12345`: `requester01/02`, `warehouse01/02`, `purchasing01/02`, `manager01/02`.
