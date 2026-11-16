# Goods Receipt và Stock Adjustment

Owner: Nguyễn Đức Hoàng Anh. Phiên bản: 2026-11-15. Trạng thái: đã triển khai trong mã nguồn, chưa build hoặc kiểm thử runtime.

Tài liệu này mô tả riêng GR/SA để bàn giao cho người tổng hợp API. Phân quyền và trạng thái bám theo mã nguồn nền tảng, README và schema hiện có; đây không phải biên bản xác nhận contract của cả nhóm. Không thay đổi API của các module khác.

## Quy ước chung

- Base path `/api`; JSON UTF-8; mọi endpoint yêu cầu `Authorization: Bearer <JWT>`.
- Actor lấy từ `CurrentUser`; transaction manager chung tự đặt actor, requestId và IP trước nghiệp vụ.
- Không nhận `status`, `createdBy`, `receivedBy`, `confirmedBy`, thời gian hệ thống, `systemQuantity` hoặc `difference` từ body. Trường ngoài DTO bị từ chối với `400 UNKNOWN_FIELD`.
- ID phải là số nguyên dương. Số lượng có tối đa 11 chữ số phần nguyên và 3 chữ số thập phân, tương ứng `numeric(14,3)`; không tự làm tròn dữ liệu đầu vào.
- Danh sách dùng `status` tùy chọn, `page` mặc định 0, `size` mặc định 20 trong khoảng 1–100. Thứ tự cố định `createdAt DESC, id DESC`.
- Response danh sách: `{ "items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`.
- PUT thay thế toàn bộ phần nội dung có thể sửa và toàn bộ dòng hàng; ID dòng có thể thay đổi. Không xóa vật lý chứng từ.
- Lỗi dùng `{ "status", "code", "message", "requestId" }`, có thể có `details` theo bộ xử lý chung.

## Goods Receipt

Tất cả thao tác GR chỉ dành cho `WAREHOUSE_STAFF`, đúng với controller đã có. Warehouse được xử lý phiếu GR của bộ phận kho; không áp dụng quy tắc Requester chỉ xem phiếu của mình của PR/SI sang GR.

| Method | Path | operationId | Thành công | Điều kiện |
|---|---|---|---|---|
| GET | `/goods-receipts` | grList | 200, trang GR | Lọc theo status tùy chọn |
| GET | `/goods-receipts/{id}` | grGet | 200, GR | Phiếu tồn tại |
| POST | `/goods-receipts` | grCreate | 201, GR | PO `ORDERED` hoặc `PARTIALLY_RECEIVED` |
| PUT | `/goods-receipts/{id}` | grUpdate | 200, GR | GR `DRAFT`, PO còn được nhận |
| POST | `/goods-receipts/{id}/confirm` | grConfirm | 200, GR | GR `DRAFT`, PO còn được nhận |
| POST | `/goods-receipts/{id}/cancel` | grCancel | 200, GR | GR `DRAFT`, lý do không rỗng |

### Body

Create: `purchaseOrderId` bắt buộc; `documentReference` tùy chọn, tối đa 100 ký tự; `note` tùy chọn, tối đa 4000 ký tự; `items` có 1–100 dòng khác null. Mỗi dòng có `purchaseOrderItemId` bắt buộc và `receivedQuantity >= 0.001`. Không được lặp dòng PO hoặc chọn dòng thuộc PO khác.

```json
{
  "purchaseOrderId": 12,
  "documentReference": "DELIVERY-2026-001",
  "note": "Nhận đợt 1",
  "items": [{ "purchaseOrderItemId": 31, "receivedQuantity": 2.500 }]
}
```

Update dùng cùng các trường nội dung trên, bỏ `purchaseOrderId` vì PO của phiếu bất biến. Confirm chỉ nhận `{ "documentReference": "DELIVERY-2026-001" }`, bắt buộc không rỗng và tối đa 100 ký tự. Cancel chỉ nhận `{ "reason": "Lập nhầm phiếu" }`, bắt buộc không rỗng, tối đa 4000 ký tự. Chuỗi nội dung được trim; note/reference tùy chọn chỉ có khoảng trắng được lưu null.

### Response

Các trường: `id`, `code`, `purchaseOrderId`, `receivedBy`, `receivedAt`, `confirmedBy`, `confirmedAt`, `status`, `documentReference`, `note`, `cancelReason`, `items`. Mỗi dòng có `id`, `purchaseOrderItemId`, `receivedQuantity`. `receivedAt` và `confirmedAt` do DB đặt khi xác nhận, null trước xác nhận; thời gian trả dưới dạng ISO-8601/UTC.

### Nhận nhiều đợt và transaction

GR hỗ trợ nhận một phần theo schema IN17 hiện có. Với mỗi dòng: `receivedQuantity <= orderedQuantity - SUM(receivedQuantity của GR CONFIRMED)`. Draft khác không giữ chỗ số lượng. Điều kiện được kiểm tra khi tạo, sửa và kiểm tra lại khi confirm.

Create khóa PO. Update/confirm khóa GR trước, rồi khóa PO. Hai phiếu cùng PO phải chờ nhau trước khi tính phần còn lại. Trigger DB kiểm tra thêm lần cuối, khóa dòng PO và số tồn, ghi ledger rồi tự cập nhật trạng thái PO thành `PARTIALLY_RECEIVED` hoặc `RECEIVED`. Backend không sửa PO hoặc tồn kho trực tiếp.

Confirm chỉ đổi `confirmedBy` và `status`, kèm số chứng từ giao hàng. Lỗi ở bất kỳ dòng nào khiến toàn bộ transaction rollback. Confirm lặp trả `409 INVALID_STATE` và không ghi thêm tồn. PO `DRAFT`, `RECEIVED`, `CANCELED` không được dùng để tạo/sửa/xác nhận GR; GR đã `CONFIRMED` hoặc `CANCELED` không được sửa, confirm hoặc cancel lại. Draft vẫn có thể hủy nếu PO đã bị hủy.

## Stock Adjustment

Luồng hiện có trong DB là `DRAFT → CONFIRMED | CANCELED`. Không có trạng thái `SUBMITTED`, nên không thêm endpoint submit hoặc approve tách biệt; Manager duyệt bằng `/confirm`.

| Method | Path | operationId | Role | Thành công |
|---|---|---|---|---|
| GET | `/stock-adjustments` | saList | Warehouse hoặc Manager | 200, trang SA |
| GET | `/stock-adjustments/{id}` | saGet | Warehouse hoặc Manager | 200, SA |
| POST | `/stock-adjustments` | saCreate | Warehouse | 201, SA DRAFT |
| PUT | `/stock-adjustments/{id}` | saUpdate | Warehouse | 200, SA DRAFT |
| POST | `/stock-adjustments/{id}/confirm` | saConfirm | Manager, khác người lập | 200, SA CONFIRMED |
| POST | `/stock-adjustments/{id}/cancel` | saCancel | Warehouse | 200, SA CANCELED |

Quyền tạo/xác nhận theo trigger và README; quyền xem/sửa/hủy được triển khai cho bộ phận kho, Manager chỉ xem và xác nhận. Không thêm kiểm tra owner như PR/SI. Nhóm cần review các lựa chọn contract này trước phát hành, vì tài liệu phân công ghi nhận đây là các điểm cần chốt và repo không có biên bản xác nhận riêng.

### Body

Create và update có cùng cấu trúc, mỗi action dùng một DTO riêng:

```json
{
  "adjustmentType": "STOCKTAKE",
  "reason": "Đối chiếu kiểm kê thực tế",
  "items": [
    { "productId": 7, "countedQuantity": 8.000, "note": "Kiểm đếm tại kệ A" }
  ]
}
```

- `adjustmentType`: bắt buộc, một trong `OPENING`, `STOCKTAKE`, `CORRECTION`.
- `reason`: bắt buộc không rỗng, tối đa 4000 ký tự; được trim.
- `items`: 1–100 dòng khác null, sản phẩm phải tồn tại và không lặp. Cho phép sản phẩm đã ngừng hoạt động để đối chiếu lượng hàng còn lại; SA không tạo giao dịch mua mới.
- Mỗi dòng: `productId` bắt buộc; `countedQuantity >= 0`; `note` tùy chọn, tối đa 4000 ký tự, trim và đổi chuỗi rỗng thành null.
- Confirm yêu cầu body `{}`. Cancel yêu cầu `{ "reason": "Lập nhầm phiếu kiểm kê" }`, tối đa 4000 ký tự và không rỗng.

### Response và ảnh hưởng tồn

Các trường: `id`, `code`, `adjustmentType`, `createdBy`, `confirmedBy`, `status`, `reason`, `cancelReason`, `createdAt`, `confirmedAt`, `items`. Mỗi dòng có `id`, `productId`, `countedQuantity`, `systemQuantity`, `difference`, `note`.

`countedQuantity` là **tồn thực đếm cuối cùng**, không phải lượng tăng/giảm. Trước confirm, `systemQuantity` và `difference` là null. Trigger khóa số tồn theo productId tăng dần, lấy snapshot tại thời điểm confirm, tính chênh lệch rồi ghi `ADJUST_IN` hoặc `ADJUST_OUT`; bằng nhau thì không ghi ledger mới. Ví dụ tồn lúc confirm bằng 10, đếm 8: snapshot 10, difference -2, tồn cuối cùng 8.

`OPENING` chỉ được xác nhận khi tồn hiện tại bằng 0 theo trigger hiện có. Mã phiếu, người lập và thời gian do server/DB quản lý. Người lập dù sau này đổi sang role Manager cũng không được tự confirm phiếu cũ. Service khóa SA trước khi kiểm tra trạng thái và SoD; trigger khóa stock balances và thực hiện posting. Sau flush, service refresh các dòng để response nhận được snapshot mà trigger vừa ghi.

Update, confirm và cancel chỉ áp dụng cho DRAFT. Xác nhận lại hoặc sửa/hủy phiếu đã kết thúc trả `409 INVALID_STATE`. Lỗi posting khiến cả trạng thái, snapshot, tồn và ledger của transaction rollback. Hai SA khác nhau được xử lý theo thứ tự khóa DB; mỗi SA áp dụng lượng đếm tuyệt đối tại thời điểm xác nhận, không cộng dồn các lượng đếm từ client.

## Lỗi và audit

| HTTP/code | Tình huống |
|---|---|
| 400 VALIDATION_ERROR | ID không dương, thiếu/trống nội dung, dòng rỗng/lặp, sai số lượng/độ chính xác, tham chiếu không hợp lệ |
| 400 UNKNOWN_FIELD | Body chứa status, actor, systemQuantity hoặc trường ngoài DTO |
| 401 UNAUTHENTICATED / TOKEN_EXPIRED | Không có JWT hợp lệ hoặc token hết hạn, theo lớp security chung |
| 403 FORBIDDEN_ROLE | Role không có quyền gọi endpoint |
| 403 SOD_VIOLATION | Manager là người đã lập chính SA đó |
| 404 NOT_FOUND | Phiếu hoặc PO yêu cầu không tồn tại |
| 409 OVER_RECEIPT | Tổng nhận vượt số lượng PO |
| 409 INVALID_STATE | Sai trạng thái, confirm lặp, OPENING khi tồn khác 0, hoặc xung đột khóa do DB ánh xạ |

Service dùng `ApiException` và bộ xử lý lỗi chung. Vi phạm role/SoD/trạng thái dùng cơ chế audit đã có; trigger ghi audit của thao tác dữ liệu. Module không INSERT/UPDATE trực tiếp `stock_transactions`, `audit_logs` hoặc `stock_balances.quantity`.

## Bàn giao và demo

Ví dụ request có trong `postman/P03_HoangAnh_GR_SA.postman_collection.json`. Token để trống khi chia sẻ. Điền ID PO đã phát hành và dòng PO từ module PO, ID sản phẩm có sẵn; các số trong ví dụ không phải ID seed được bảo đảm tồn tại.

Demo GR: Warehouse tạo Draft nhận một phần PO → xem/sửa Draft → confirm kèm chứng từ giao hàng → xem thời gian xác nhận → dùng module tồn/ledger hiện có để tra cứu → tạo đợt nhận còn lại nếu cần. Demo SA: Warehouse tạo/sửa phiếu STOCKTAKE → Manager khác người lập xem và confirm → xem snapshot/chênh lệch trả về. Phiếu lập nhầm được cancel khi còn DRAFT.

OpenAPI của hai module sinh từ controller, có tag và operationId riêng để người tổng hợp ghép vào spec chung. Chưa chạy backend để xuất hoặc xác minh `/v3/api-docs`. Không có kết quả build, kiểm thử, rollback đồng thời hoặc bằng chứng restore trong lần bàn giao này, theo yêu cầu không kiểm thử và không tải môi trường của người dùng. Runbook IA-IN08 nằm ở `docs/HOANG_ANH_BACKUP_RESTORE.md`.
