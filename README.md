# P03 – Inventory & Purchase Request Management (Backend)

Spring Boot 4 · Java 21 · PostgreSQL 16 (Neon cho môi trường chung). Quy ước code: **[CONTRIBUTING.md](CONTRIBUTING.md)**.
Database: **[db/README.md](db/README.md)**.

## Chạy backend trong IntelliJ

1. Mở thư mục repo. Nếu Maven sync lỗi `Guice/MissingImplementation`: Settings → Build Tools → Maven →
   Maven home path = **Bundled (Maven 3)**.
2. Run → Edit Configurations → `P03InventoryApplication` → **Environment variables**:
   ```
   DB_HOST=<host Neon>;DB_NAME=<db>;DB_USER=<user>;DB_PASSWORD=<pass>;JWT_SECRET=<>=32 ký tự>;SPRING_PROFILES_ACTIVE=dev
   ```
   Sinh JWT_SECRET (PowerShell): `[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))`
3. Run. Kiểm tra: http://localhost:8080/actuator/health → `UP`; Swagger: http://localhost:8080/swagger-ui.html

Postgres local thay cho Neon: `DB_HOST=localhost:5432`, `DB_SSLMODE=disable`, chạy các file trong `db/init` theo thứ tự.

## Thử nhanh

```
POST /api/auth/login   {"username":"manager01","password":"Demo@12345"}
→ {"accessToken":"…","tokenType":"Bearer","expiresIn":1800,"role":"MANAGER",…}
GET  /api/products?q=a4        (Authorization: Bearer <accessToken>)
```

Trong Swagger UI, gọi `POST /api/auth/login`, sao chép `accessToken`, bấm **Authorize** và nhập token (không cần tự thêm tiền tố `Bearer`). Sau đó gọi các API có khóa bảo mật.

## Luồng nghiệp vụ chính

| Luồng | Endpoint | Vai trò |
|---|---|---|
| Purchase Request | `/api/pr` (tạo, sửa, gửi, duyệt, từ chối, hủy) | Requester tạo; Manager duyệt/từ chối |
| Purchase Order | `/api/po` (tạo, sửa, phát hành, hủy) | Purchasing; chỉ từ PR đã duyệt |
| Goods Receipt | `/api/goods-receipts` (tạo, sửa, xác nhận, hủy) | Warehouse |
| Stock Issue | `/api/stock-issues` (tạo, sửa, xác nhận, từ chối, hủy) | Người dùng tạo/hủy yêu cầu của mình; Warehouse xác nhận/từ chối |
| Stock Adjustment | `/api/stock-adjustments` (tạo, sửa, duyệt, hủy) | Warehouse lập; Manager khác người lập duyệt |
| Supplier | `/api/suppliers` (quản lý, kích hoạt lại) | Purchasing quản lý; Manager kích hoạt lại |

## Lệnh

| Việc | Lệnh |
|---|---|
| Build + test | `./mvnw test` (Windows: `.\mvnw.cmd test`) |
| Toàn hệ thống bằng Docker | `cp .env.example .env` → sửa giá trị → `docker compose up -d --build` |

## API của khung (member1)

| Method | URL | Vai trò |
|---|---|---|
| POST | `/api/auth/login` | Public |
| GET | `/api/auth/me` | Đã đăng nhập |
| GET/POST | `/api/users`, GET `/api/users/{id}` | Manager |
| POST | `/api/users/{id}/lock` | Manager |
| PUT | `/api/users/{id}/role` | Manager |
| GET | `/api/categories` | Đã đăng nhập |
| POST/PUT | `/api/categories[/{id}]` | Warehouse, Manager |
| GET | `/api/products?q=&categoryId=&activeOnly=&page=&size=`, `/api/products/{id}` | Đã đăng nhập |
| POST/PUT | `/api/products[/{id}]` | Warehouse, Manager |
| PUT | `/api/products/{id}/min-stock` | Manager |

Lỗi luôn có dạng `{ "status": 409, "code": "INVALID_STATE", "message": "…", "requestId": "…" }`.
