# Contract SI — bản để nhóm review, chưa phê duyệt

Ngày: 10/11/2026. Owner: Dương Quỳnh Anh Khoa. Phiên bản: 0.1.0-review.

## Nguồn và phạm vi

Đối chiếu source StockIssueController/Service/DTO, Auth DTO, Paging, ErrorCode/GlobalExceptionHandler trong bản BE đã dùng để triển khai; tài liệu `Phân công P03 - Backend.docx`, mục 4.6; `03_System_Security_Design_P03.docx` (SDD). Dựa vào ảnh PR #3 và kết quả local được cung cấp, không phải lần kiểm tra repo remote mới nhất.

Tuần 2: chốt contract SI, tạo khung OpenAPI/Postman và gửi mẫu nhóm. Tuần 4: hoàn thành submit/confirm/reject, test quyền và tồn kho. Bộ này chuẩn bị để chốt; chỉ được gọi là đã chốt sau khi owner liên quan xác nhận. Chưa gửi thay Khoa.

## A. API đã triển khai trong PR #3

| Method/path | Quyền hiện tại | Request | Thành công |
|---|---|---|---|
| POST /api/stock-issues | REQUESTER / WAREHOUSE_STAFF / MANAGER | reason, items | 201 Issue, DRAFT |
| GET /api/stock-issues | Ba vai trò trên, chỉ phiếu mình | page=0, size=20 | 200 IssuePage |
| GET /api/stock-issues/{id} | Ba vai trò trên, chỉ phiếu mình | id | 200 Issue |
| PUT /api/stock-issues/{id} | Ba vai trò trên, chỉ phiếu mình, DRAFT | reason, items | 200 Issue |

Tất cả cần JWT Bearer. Purchasing bị 403. Trong bản đầu Warehouse/Manager cũng chỉ thấy phiếu của mình; đây chưa phải quyền hàng đợi duyệt cuối cùng.

POST/PUT chỉ nhận reason và items[{productId,quantity}]. reason không trắng, max 4000; items 1..100; productId nguyên dương và sản phẩm phải active; quantity >0, tối đa 11 chữ số nguyên/3 chữ số thập phân. Không trùng productId. Hai giới hạn 100 dòng/4000 ký tự cần nhóm xác nhận. Không nhận actor/status từ body. PUT thay toàn bộ reason/items, ID dòng có thể thay đổi.

Response Issue: id, issueCode, requestedBy, confirmedBy (nullable), status, requestedAt, confirmedAt (nullable), reason, cancelReason (nullable), items[{id,productId,quantity}]. Không có SUBMITTED trong enum bản hiện tại. Danh sách: items/page/size/totalElements/totalPages; page>=0, size 1..100, sort id DESC; hiện chưa có lọc/search/status/sort tùy chọn.

| HTTP | Code theo source | Tình huống |
|---|---|---|
| 400 | VALIDATION_ERROR | DTO sai, trùng sản phẩm, sản phẩm thiếu/inactive, phân trang sai |
| 400 | UNKNOWN_FIELD | Trường lạ như status |
| 401 | UNAUTHENTICATED / TOKEN_EXPIRED | Thiếu hoặc hết hạn token |
| 403 | FORBIDDEN_ROLE | Sai vai trò |
| 404 | NOT_FOUND | Không tồn tại hoặc khác chủ |
| 409 | INVALID_STATE | Sửa phiếu không còn DRAFT |

Error envelope: status, code, message, requestId; details[{field,message}] có thể có khi validation. Không so khớp nguyên văn message trong client; dùng status/code. Các code 400/403/404 ở trên được đọc từ source, chưa được xác nhận body bằng ảnh test. Bộ Postman mới sẽ kiểm tra chúng; nếu thất bại cần điều tra thay vì bỏ assertion.

## B. Workflow đề xuất theo SDD — chưa triển khai

| Endpoint đề xuất | Actor | Chuyển trạng thái | Body đề xuất | Thành công đề xuất |
|---|---|---|---|---|
| POST /api/stock-issues/{id}/submit | Chủ phiếu có vai trò tạo SI | DRAFT → SUBMITTED | Không body | 200 Issue |
| POST /api/stock-issues/{id}/confirm | WAREHOUSE_STAFF, khác người tạo | SUBMITTED → CONFIRMED | Không body | 200 Issue |
| POST /api/stock-issues/{id}/reject | WAREHOUSE_STAFF, khác người tạo | SUBMITTED → CANCELED | {reason} bắt buộc | 200 Issue |
| POST /api/stock-issues/{id}/cancel | Chủ phiếu | DRAFT → CANCELED | {reason} bắt buộc, cần chốt | 200 Issue |

SDD có luồng hủy Draft của chủ phiếu nhưng chưa chốt đường dẫn/payload riêng: /cancel ở đây là đề xuất, không tuyên bố đã có API. Reject kết thúc CANCELED, không thêm REJECTED. Khóa nội dung từ SUBMITTED. Body reason của reject/cancel đề xuất max4000 để thống nhất; cần xác nhận.

Confirm phải gán actor từ token, không nhận confirmedBy. DB trigger thực hiện trừ tồn/ledger trong transaction; service không ghi stock_balances trực tiếp. Thiếu tồn đề xuất 409 INSUFFICIENT_STOCK, không đổi trạng thái/ledger/tồn. Confirm lặp đề xuất 409 INVALID_STATE, không trừ lần hai. SoD 403 SOD_VIOLATION. Cần kiểm tra đồng thời trên cùng phiếu và các phiếu khác nhau tranh cùng tồn.

## C. Quyết định cần nhóm xác nhận

| ID | Nội dung cần chốt | Người phối hợp | Trạng thái |
|---|---|---|---|
| D01 | Dùng SUBMITTED theo SDD/ADR-07 và cập nhật SRS/schema tương ứng | Minh, phụ trách DB, Khoa | Chưa xác nhận |
| D02 | Warehouse được xem phiếu SUBMITTED của người khác qua list/detail như thế nào; Manager có quyền xem gì | Nhóm backend/FE và phân quyền | Chưa xác nhận |
| D03 | Actor/SoD, endpoint /reject và /cancel; HTTP 200 hay quy ước khác | Khoa, nhóm backend/FE | Chưa xác nhận |
| D04 | Confirm lặp trả 409, thứ tự ưu tiên sai role/owner/state; audit event và requestId | Khoa, Minh, Hoàng Anh, Trung Hiếu | Chưa xác nhận |
| D05 | Giới hạn 100 dòng, reason4000, cách hiển thị decimal | Khoa, FE | Chưa xác nhận |
| D06 | Ai cập nhật migration/trigger, thời điểm tích hợp, môi trường test chung | Phụ trách DB | Chưa xác nhận |

Không chỉ thêm nhãn enum: document_status dùng chung, cần rà tác động các module GR/SA, guard trigger, posting, quyền DB, rollback và regression. Theo CONTRIBUTING, thay đổi common/db/module khác cần PR riêng phối hợp Minh. Bộ này không sửa DB hoặc Neon.

## D. Bằng chứng và giới hạn

- Local: tạo/đọc/sửa Draft, ownership GET/PUT404, list lọc owner, thêm status400, quantity0 400, duplicate400, no-token401 UNAUTHENTICATED, Purchasing list403 đã có ảnh.
- Repo clone BE-Khoa: 27 test, 26 đạt, 1 skipped, 0 failures/errors; SI 7/7 đạt. Không gọi test skipped là đạt.
- Ảnh PR #3 ngày 10/11: 2 checks đạt, ready for review, chưa merge trong ảnh.
-- Ngày 10/11/2026, đã chạy collection trên backend local:
  23 request, 47 assertions đạt, 0 failed, 0 skipped, 0 errors.
  Các kiểm tra error code/requestId trong collection đều đạt.
  Kết quả này không bao gồm workflow đề xuất hoặc Neon.
## E. Điều kiện hoàn tất Tuần 2

- [x] Chuẩn bị contract review và liệt kê điểm mở.
- [x] Chuẩn bị OpenAPI Draft, workflow đề xuất, Postman/environment và mẫu module.
- [x] Import/chạy collection trên local, giữ report và xử lý khác biệt contract.
- [ ] Gửi PR/bộ mẫu cho các owner; từng owner bổ sung contract/module của mình.
- [ ] Nhóm xác nhận D01..D06, ghi người xác nhận/ngày/link quyết định, phát hành contract đã chốt.

Không dùng checklist chuẩn bị này để tuyên bố toàn nhóm đã đồng ý.
