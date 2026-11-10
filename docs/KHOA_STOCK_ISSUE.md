# Stock Issue Draft — bàn giao phần của Khoa

Cập nhật: 09/11/2026. Người thực hiện: Dương Quỳnh Anh Khoa.

* Repo: https://github.com/KNS-OJT/BE
* Nhánh: `feature/khoa-stock-issue-draft`
* Draft PR: https://github.com/KNS-OJT/BE/pull/3
* Commit code đã push và được xác nhận qua ảnh: `56e2b22` (12 file, 285 dòng thêm).
* Phạm vi tài liệu: bản Draft đang triển khai; không tuyên bố hoàn thành toàn bộ nghiệp vụ xuất kho.

## 1\. Phạm vi đã triển khai

Tạo phiếu nháp, xem danh sách, xem chi tiết và sửa phiếu nháp. Mỗi phiếu có lý do và các dòng sản phẩm/số lượng. Người yêu cầu được lấy từ `CurrentUser`, không nhận từ request body.

Phần này chỉ thay đổi header và dòng hàng của phiếu; không trực tiếp ghi số tồn hoặc ledger. Sử dụng `ApiException`, `Paging`, `PageResponse` và transaction manager có sẵn. Các file code được bổ sung trong package `stockissue`; không thay đổi `common/`, `db/` hoặc entity của module khác.

Chưa triển khai submit/confirm/reject, danh sách chờ duyệt, hoặc luồng trừ tồn kho.

## 2\. API và quyền truy cập hiện tại

Các endpoint cần `Authorization: Bearer <accessToken>`.

|Phương thức|Endpoint|Hành vi|HTTP thành công theo controller|
|-|-|-|-|
|POST|`/api/stock-issues`|Tạo phiếu DRAFT cho người đăng nhập|201|
|GET|`/api/stock-issues?page=0\&size=20`|Danh sách phiếu của người đăng nhập|200|
|GET|`/api/stock-issues/{id}`|Chi tiết phiếu của người đăng nhập|200|
|PUT|`/api/stock-issues/{id}`|Thay lý do và toàn bộ dòng hàng của phiếu DRAFT thuộc người đăng nhập|200|

Các vai trò được vào API: `REQUESTER`, `WAREHOUSE\_STAFF`, `MANAGER`. `PURCHASING\_STAFF` bị từ chối. Trong bản đầu, cả ba vai trò được phép đều chỉ xem/sửa phiếu của chính mình. Đây là phạm vi hiện tại cần nhóm review, chưa phải quyền xem hàng đợi duyệt của Warehouse/Manager.

Chi tiết phiếu không tồn tại hoặc khác chủ trả 404. Sửa phiếu khác chủ trả 404. Sửa phiếu không còn DRAFT bị từ chối qua `invalidStateAudited`; cần kiểm thử tích hợp thêm cho trường hợp này.

### Request tạo/sửa

```json
{
  "reason": "Cap vat tu cho phong hoc",
  "items": \[
    { "productId": 4, "quantity": 3 }
  ]
}
```

`productId: 4` là dữ liệu đã dùng trên database local trong phiên test, không phải ID cố định cho mọi môi trường. Chọn sản phẩm đang hoạt động từ API sản phẩm của môi trường cần chạy.

Chỉ truyền `reason` và `items`. Không truyền `status`, `requestedBy`, `confirmedBy` hoặc các trường do hệ thống quản lý.

### Response

Chi tiết gồm: `id`, `issueCode`, `requestedBy`, `confirmedBy`, `status`, `requestedAt`, `confirmedAt`, `reason`, `cancelReason`, `items`. Mỗi dòng gồm `id`, `productId`, `quantity`.

Danh sách trả `items`, `page`, `size`, `totalElements`, `totalPages`; page bắt đầu từ 0, size mặc định 20, tối đa 100, sắp xếp ID giảm dần.

### Validation và cập nhật

* `reason`: không được trống, tối đa 4000 ký tự; trim trước khi lưu.
* `items`: có ít nhất 1 và tối đa 100 dòng; không có dòng null.
* `productId`: không null, lớn hơn 0, tồn tại và đang hoạt động.
* `quantity`: không null, lớn hơn 0; tối đa 11 chữ số nguyên và 3 chữ số thập phân.
* Không có hai dòng cùng `productId`.
* PUT thay toàn bộ các dòng; ID dòng có thể đổi sau cập nhật.
* PUT khóa header bằng `PESSIMISTIC\_WRITE`, xóa/flush dòng cũ rồi thêm dòng mới trong cùng transaction.
* Giới hạn 100 dòng và 4000 ký tự là lựa chọn triển khai cần nhóm xác nhận.

Khóa header trong Draft không phải bằng chứng đã giải quyết đồng thời cho nghiệp vụ xác nhận/trừ tồn kho.

## 3\. Môi trường và cách chạy

Yêu cầu: JDK 21, Maven Wrapper của repo, PostgreSQL phù hợp schema. Phiên kiểm thử dùng PostgreSQL 16 trong Docker và backend trên máy Windows tại `http://localhost:8081`.

Khởi động database local từ thư mục repo:

```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps
```

Thiết lập `.env` từ `.env.example` theo README của repo. Kết nối đã dùng: `DB\_HOST=localhost:5432`, `DB\_NAME=p03`, `DB\_USER=p03\_app`, `DB\_SSLMODE=disable`. Tự cấu hình mật khẩu local và JWT secret; không commit `.env`, mật khẩu hoặc token.

Trong cửa sổ chạy backend, nạp biến môi trường rồi chạy:

```powershell
Get-Content .env | ForEach-Object {
    if ($\_ -match '^\\s\*(\[^#=\\s]+)\\s\*=(.\*)$') {
        \[Environment]::SetEnvironmentVariable($matches\[1], $matches\[2].Trim(), 'Process')
    }
}
$env:SERVER\_PORT = '8081'
.\\mvnw.cmd spring-boot:run
```

Giữ cửa sổ backend mở. Dùng cửa sổ khác để gọi API. Health: `/actuator/health`; Swagger theo cấu hình dev của repo: `/swagger-ui.html`. Đăng nhập qua `/api/auth/login` bằng tài khoản demo đã seed trong môi trường local.

Docker database local và Neon là hai môi trường riêng. Push code lên GitHub không chuyển dữ liệu local sang Neon. Phiên test được ghi dưới đây không xác nhận hoạt động trên Neon.

## 4\. Kết quả kiểm thử đã ghi nhận

Nguồn bằng chứng: ảnh PowerShell và trình duyệt do Khoa cung cấp ngày 08–09/11/2026. Đây là kết quả quan sát từ các lần chạy đó, không phải một lần chạy mới do người soạn tài liệu thực hiện.

### Unit test

```powershell
.\\mvnw.cmd "-Dtest=StockIssueServiceTest" test
```

Trên bản clone `BE-Khoa`: 7 test, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS` (ảnh kết thúc lúc 23:08 ngày 09/11/2026).

Trên thư mục cũ `BE-main`: toàn bộ suite từng báo 27 test, 0 failures, 0 errors, 1 skipped. Không dùng kết quả đó để tuyên bố đã chạy toàn bộ suite trên bản clone mới. Trước khi xin review chính thức, chạy `./mvnw test` theo CONTRIBUTING và ghi nhận test bị skip nếu có.

### Kiểm thử API thực tế trên local

|Mã|Trường hợp|Kết quả quan sát|
|-|-|-|
|SI-D01|Health backend|`status: UP`|
|SI-D02|Đăng nhập requester01, lấy sản phẩm|Thành công; vai trò REQUESTER|
|SI-D03|Tạo Draft với sản phẩm 4, số lượng 2|Tạo được phiếu ID 1, DRAFT|
|SI-D04|Chủ phiếu sửa số lượng thành 3, đọc lại|DRAFT, lý do mới, quantity 3|
|SI-D05|requester02 đọc phiếu ID 1 của requester01|HTTP 404|
|SI-D06|requester02 lấy danh sách khi chưa tạo phiếu|items rỗng, totalElements 0; không lộ phiếu ID 1|
|SI-D07|requester02 sửa phiếu ID 1|HTTP 404; đọc lại bằng chủ phiếu thấy dữ liệu giữ nguyên|
|SI-D08|Chủ phiếu gửi thêm status=CONFIRMED trong PUT|HTTP 400; trạng thái vẫn DRAFT, lý do giữ nguyên|
|SI-D09|PUT số lượng 0|HTTP 400; lý do, trạng thái và số lượng 3 giữ nguyên|
|SI-D10|GET chi tiết không gửi token|HTTP 401; body có code UNAUTHENTICATED|
|SI-D11|purchasing01 đăng nhập rồi GET danh sách|Vai trò PURCHASING\_STAFF; HTTP 403|
|SI-D12|PUT hai dòng cùng sản phẩm 4|HTTP 400; vẫn một dòng sản phẩm 4, quantity 3|

Giới hạn bằng chứng: các ảnh lỗi 400/403/404 chưa hiển thị response body nên chưa xác nhận được từng error code, message và requestId. Không suy diễn HTTP 400 thành đã kiểm chứng mã UNKNOWN\_FIELD. Chưa đối chiếu audit log trong database. Chưa kiểm tra toàn bộ ma trận role/endpoint hoặc giới hạn validation.

Ảnh PR #3 lúc 23:18 ngày 09/11/2026: Draft, không xung đột với main, 1 check thành công và 1 check đang chạy. Cần kiểm tra lại trạng thái CI hiện tại; chưa ghi nhận cả hai check đều đạt.

## 5\. Phần còn lại và quyết định cần nhóm chốt

1. Chốt role matrix cho Warehouse/Manager xem, xác nhận/từ chối phiếu của người khác; chốt quy tắc không tự duyệt (SoD).
2. Thiết kế có bước SUBMITTED nhưng enum database đã kiểm tra chỉ có DRAFT/CONFIRMED/CANCELED. Cần PR database riêng phối hợp với người phụ trách, theo CONTRIBUTING. Không chỉ thêm enum: rà soát trigger chuyển trạng thái, posting, tác động lên các chứng từ dùng chung enum và kiểm thử hồi quy.
3. Sau khi thống nhất schema/workflow: triển khai submit, confirm và reject/cancel theo hợp đồng API được duyệt; không cho client tự truyền người duyệt/trạng thái qua DTO cập nhật Draft.
4. Tồn kho và ledger do trigger xử lý; không cập nhật tồn trực tiếp trong service.
5. Kiểm thử thiếu tồn kho, rollback, tự duyệt, sai trạng thái, xác nhận lặp và xác nhận đồng thời; đối chiếu audit/ledger/tồn trước và sau.
6. Hoàn thiện tài liệu OpenAPI, bộ request Postman và bằng chứng kiểm thử theo phân công nhóm.

## 6\. Checklist bàn giao PR Draft

* \[x] Code và unit test đã push lên nhánh riêng; Draft PR #3 đã tạo.
* \[x] 7/7 test StockIssueServiceTest đạt trên bản clone mới.
* \[x] Có bằng chứng các tình huống API local trong bảng trên.
* \[ ] Thêm tài liệu này vào `docs/KHOA\_STOCK\_ISSUE.md` và push lên cùng nhánh.
* \[ ] Chạy toàn bộ test trên bản clone mới, ghi nhận failures/errors/skipped.
* \[ ] Kiểm tra CI mới nhất; hoàn thiện checklist PR theo bằng chứng thực tế.
* \[x] Nhóm review phạm vi quyền và giới hạn validation; cập nhật theo góp ý.

PR Draft này có thể được review độc lập với giai đoạn duyệt phiếu nếu nhóm đồng ý phạm vi. Hoàn thành phần Draft không đồng nghĩa hoàn thành toàn bộ nhiệm vụ Stock Issue. Tài liệu này không xác nhận đã merge PR hoặc thay đổi Neon.

