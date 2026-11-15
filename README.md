# P03 – Inventory & Purchase Request Management (Backend)

Spring Boot 4 · Java 21 · PostgreSQL 16 (Neon cho môi trường chung). Quy ước code: **[CONTRIBUTING.md](CONTRIBUTING.md)**.
Database: **[db/README.md](db/README.md)**.

## Chạy backend trong IntelliJ

1. Mở thư mục repo. Nếu Maven sync lỗi `Guice/MissingImplementation`: Settings → Build Tools → Maven →
   Maven home path = **Bundled (Maven 3)**.
2. Run → Edit Configurations → `P03_Inventory` → **Environment variables**:
   ```
   DB_HOST=<direct host Neon, không có -pooler>;DB_NAME=<db>;DB_USER=p03_app;DB_PASSWORD=<app-secret>;DB_SSLMODE=verify-full;DB_CHANNEL_BINDING=require;JWT_SECRET=<secret ít nhất 32 byte>;SPRING_PROFILES_ACTIVE=dev
   ```
   Backend dùng role `p03_app` ít quyền; giữ role owner cho thao tác quản trị thủ công, không dùng owner làm tài khoản chạy ứng dụng.
   Sinh JWT_SECRET bằng RNG bảo mật (PowerShell):
   ```powershell
   $rng = [Security.Cryptography.RandomNumberGenerator]::Create(); $bytes = New-Object byte[] 48
   $rng.GetBytes($bytes); [Convert]::ToBase64String($bytes); $rng.Dispose()
   ```
3. Run. Kiểm tra: http://localhost:8080/actuator/health → `UP`; Swagger: http://localhost:8080/swagger-ui.html

Đặt CA PEM của ISRG Root X1 tại `~/.postgresql/root.crt` (Windows: `%APPDATA%\postgresql\root.crt`) để JDBC xác minh CA và hostname với `sslmode=verify-full`. Neon bắt buộc TLS; ứng dụng dùng direct endpoint, xác minh chứng chỉ đầy đủ và yêu cầu SCRAM channel binding. Postgres local không bật TLS: đặt `DB_HOST=localhost:5432`, `DB_SSLMODE=disable`, `DB_CHANNEL_BINDING=disable`, rồi chạy các file trong `db/init` theo thứ tự.

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
| Môi trường lab (PostgreSQL local, dữ liệu demo) | `docker compose up -d --build` |
| Backend production dùng Neon | `docker compose --env-file /etc/p03/deploy.env -f deploy/docker-compose.neon.yml up -d --build` |

Windows cần `JAVA_HOME` trỏ tới JDK 21 trước khi chạy Maven. Production compose dùng role `p03_app`, profile `prod`, secret files (DB password/JWT/CA), và chỉ bind backend vào loopback. Cần cấu hình reverse proxy hoặc load balancer có HTTPS trên server để frontend truy cập; đặt `CORS_ALLOWED_ORIGINS` đúng origin của frontend. Không mở trực tiếp cổng 8080 ra Internet, không bật profile `dev`/Swagger, và không dùng `.env.example` làm cấu hình production. Xem [Production deployment](deploy/README.md).

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
