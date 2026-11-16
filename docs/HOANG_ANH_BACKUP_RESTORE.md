# Runbook backup và restore IA IN08

Phụ trách: Nguyễn Đức Hoàng Anh. Phạm vi: sao lưu database P03 và khôi phục vào một database mới để đối chiếu dữ liệu, ledger và audit. Sử dụng script sẵn có `db/tests/06_backup_restore.sh`; không sửa schema, tài khoản hoặc module của thành viên khác.

Trạng thái bàn giao 2026-11-15: đã chuẩn bị hướng dẫn; **chưa thực hiện backup/restore, chưa có bằng chứng restore thành công**. Người dùng yêu cầu không kiểm thử và không tải môi trường. Các lệnh dưới đây dành cho người vận hành chạy sau trên môi trường đã được chuẩn bị; không phải kết quả chạy thực tế.

## Điều kiện vận hành

- Chạy tại thư mục gốc repo trong Bash trên máy vận hành đã có `pg_dump`, `pg_restore`, `createdb`, `psql`, `age`, `sha256sum` và các tiện ích chuẩn mà script sử dụng. Runbook không tự cài hay tải công cụ; PowerShell không chạy trực tiếp file `.sh`.
- Client PostgreSQL phải tương thích với phiên bản server; `pg_dump` không được cũ hơn major version server. Dùng kết nối direct, không dùng endpoint transaction pooler. Với Neon, đặt `PGSSLMODE=verify-full` và CA tin cậy qua `PGSSLROOTCERT` nếu libpq yêu cầu.
- Backup dùng `p03_backup` đã được cấp SELECT; không dùng tài khoản ứng dụng để dump. Restore dùng tài khoản quản trị được phép tạo database mới trên máy/cluster phục hồi biệt lập.
- Mật khẩu lấy từ secret manager hoặc `PGPASSFILE` riêng với quyền truy cập hạn chế; không đưa mật khẩu, connection string chứa mật khẩu, JWT hoặc private key vào câu lệnh, Git hay log bằng chứng.
- Cung cấp sẵn public key `AGE_RECIPIENT` và private key tại `AGE_IDENTITY_FILE` bên ngoài repo. Lưu private key tách biệt với backup. Không tạo hoặc gửi key trong log bàn giao.
- Đảm bảo đủ dung lượng: script restore giải mã ra file tạm, tự xóa khi thoát. Đặt `TMPDIR` vào volume an toàn trên máy vận hành; giới hạn truy cập OS.
- Chỉ vận hành trên nguồn/đích được phân công. Không restore đè database đang dùng; không drop database có sẵn. Nếu tên đích đã tồn tại, chọn tên mới. Script gọi `createdb` và dừng nếu không tạo được.

## Tạo backup

Dừng tạm mọi luồng ghi nghiệp vụ của nguồn trong cửa sổ backup nếu cần đối chiếu số dòng chính xác. Script dùng snapshot nhất quán của `pg_dump`, nhưng các truy vấn đếm độc lập trước/sau dump không cùng snapshot nếu nguồn vẫn đang ghi.

Chuẩn bị biến môi trường cho nguồn. Chạy các bước trong cùng Bash shell; chế độ dừng khi lỗi ngăn việc tiếp tục backup sau khi lấy baseline thất bại. Các giá trị bên dưới là chỗ điền, không chứa thông tin kết nối thật:

```bash
set -euo pipefail
export PGHOST='<source-direct-host>'
export PGPORT='5432'
export PGDATABASE='<source-database>'
export PGUSER='p03_backup'
export PGPASSFILE='/secure/source.pgpass'
export PGSSLMODE='verify-full'
export PGSSLROOTCERT='/secure/root.crt'
export AGE_RECIPIENT='<backup-public-key>'
export BACKUP_DIR='/secure/p03/backups'
export RETENTION_DAYS='14'
umask 077
mkdir -p "$BACKUP_DIR/evidence"
```

Trước dump, lưu baseline vào thư mục evidence riêng của lần chạy, trong lúc nguồn đã ngừng ghi. Chọn tên lần chạy và giữ nguyên cho các bước sau:

```bash
export EVIDENCE_DIR="$BACKUP_DIR/evidence/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$EVIDENCE_DIR"
psql -X -v ON_ERROR_STOP=1 -At -F ',' > "$EVIDENCE_DIR/source-counts.csv" <<'SQL'
SELECT 'audit_logs', count(*) FROM audit_logs
UNION ALL SELECT 'categories', count(*) FROM categories
UNION ALL SELECT 'goods_receipt_items', count(*) FROM goods_receipt_items
UNION ALL SELECT 'goods_receipts', count(*) FROM goods_receipts
UNION ALL SELECT 'products', count(*) FROM products
UNION ALL SELECT 'purchase_order_items', count(*) FROM purchase_order_items
UNION ALL SELECT 'purchase_orders', count(*) FROM purchase_orders
UNION ALL SELECT 'purchase_request_items', count(*) FROM purchase_request_items
UNION ALL SELECT 'purchase_requests', count(*) FROM purchase_requests
UNION ALL SELECT 'roles', count(*) FROM roles
UNION ALL SELECT 'stock_adjustment_items', count(*) FROM stock_adjustment_items
UNION ALL SELECT 'stock_adjustments', count(*) FROM stock_adjustments
UNION ALL SELECT 'stock_balances', count(*) FROM stock_balances
UNION ALL SELECT 'stock_issue_items', count(*) FROM stock_issue_items
UNION ALL SELECT 'stock_issues', count(*) FROM stock_issues
UNION ALL SELECT 'stock_transactions', count(*) FROM stock_transactions
UNION ALL SELECT 'suppliers', count(*) FROM suppliers
UNION ALL SELECT 'users', count(*) FROM users
ORDER BY 1;
SQL
bash db/tests/06_backup_restore.sh backup > "$EVIDENCE_DIR/backup.log" 2>&1
```

Kiểm tra exit code ngay sau lệnh và chỉ ghi nhận backup thành công khi bằng 0. Log nêu đường dẫn file `.dump.age`; giữ file đó cùng `.sha256`, baseline và nhật ký lần chạy. Script tự dùng thư mục mode 700, file mode 600, `umask 077`, mã hóa age và checksum SHA-256. Script cũng xóa các file backup/checksum quá `RETENTION_DAYS`, nên phải chọn chính sách lưu trữ được giao trước khi chạy. Sao chép backup đã mã hóa và checksum sang nơi lưu trữ tách biệt; checksum cần nằm trong kênh lưu trữ tin cậy để phát hiện thay đổi, không thay thế chữ ký số.

Có thể cho nguồn tiếp tục ghi sau khi dump kết thúc thành công và đã lưu baseline. Giữ lại baseline gắn đúng lần dump đó. Chính sách tham chiếu từ script: chạy hằng ngày lúc 01:30 theo timezone của máy vận hành, giữ 14 ngày; việc cấu hình lịch do người vận hành thực hiện, runbook không tự tạo lịch.

## Restore vào database mới

Chuyển biến kết nối sang cluster phục hồi, dùng tài khoản quản trị được giao. `PGDATABASE` đặt thành database quản trị để tránh vô tình dùng tên nguồn; script truyền tên target cụ thể khi restore/verify.

```bash
export PGHOST='<restore-direct-host>'
export PGPORT='5432'
export PGDATABASE='postgres'
export PGUSER='<restore-operator>'
export PGPASSFILE='/secure/restore.pgpass'
export AGE_IDENTITY_FILE='/secure/backup-identity.txt'
export TMPDIR='/secure/restore-tmp'
export BACKUP_FILE='/secure/p03/backups/<exact-backup-name>.dump.age'
export RESTORE_DB='p03_restore_<unique-run-id>'
bash db/tests/06_backup_restore.sh restore "$BACKUP_FILE" "$RESTORE_DB" > "$EVIDENCE_DIR/restore.log" 2>&1
```

Giữ TLS/CA phù hợp với cluster đích. Script dừng ngay khi checksum sai, giải mã lỗi, `createdb` lỗi, restore lỗi hoặc đối chiếu integrity thất bại. Không đánh dấu PASS chỉ vì thấy database mới tồn tại. `pg_restore --no-owner` tạo đối tượng với owner của người restore; dump không bao gồm cấu hình role/password/grant cấp cluster do script dùng `--no-privileges`. Vì vậy database phục hồi dùng cho đối chiếu, chưa được coi là môi trường ứng dụng sẵn sàng. Nếu cần đưa vào vận hành, người quản trị nền tảng phải khôi phục quyền theo runbook triển khai riêng; không kết nối backend bằng owner.

Sau exit code 0, log phải có PASS cho ledger chain, audit chain và reconciliation. Script gọi `fn_verify_stock_ledger()`, `fn_verify_audit_log()` và kiểm tra `v_stock_reconciliation` không có chênh lệch. Log cũng in số dòng của products, purchase_requests, stock_transactions và audit_logs.

Đối chiếu toàn bộ baseline bằng cách chạy lại đúng truy vấn đếm ở bước backup với `psql -X -d "$RESTORE_DB" -v ON_ERROR_STOP=1 -At -F ','`, ghi ra `$EVIDENCE_DIR/restored-counts.csv`, rồi:

```bash
diff -u "$EVIDENCE_DIR/source-counts.csv" "$EVIDENCE_DIR/restored-counts.csv" > "$EVIDENCE_DIR/counts.diff"
```

Exit code 0 và diff rỗng mới xác nhận số dòng khớp. Nếu nguồn đã ghi trong lúc lấy baseline/dump, ghi rõ giới hạn đó và thực hiện lại ở cửa sổ phù hợp; không sửa số liệu để làm khớp. Có thể kiểm tra GR/SA liên quan qua truy vấn chỉ đọc trên đích. Không ghi thử vào ledger, audit hoặc tồn.

## Ghi bằng chứng và xử lý lỗi

Tạo bản ghi riêng sau mỗi lần chạy thật với các mục:

| Mục | Giá trị hiện tại |
|---|---|
| Người vận hành và thời gian UTC | Chưa thực hiện |
| Commit/schema nguồn | Chưa ghi nhận |
| Nguồn/cluster đích, không kèm secret | Chưa ghi nhận |
| Tên backup và SHA-256 | Chưa có |
| Exit code backup/restore | Chưa chạy |
| Tên database restore mới | Chưa tạo |
| Baseline và số dòng phục hồi | Chưa đối chiếu |
| Ledger hash chain / audit hash chain | Chưa xác minh |
| Stock reconciliation | Chưa xác minh |
| Log, checksum, counts.diff | Chưa có bằng chứng thực tế |
| Kết luận IA-IN08 | Chưa đủ điều kiện ghi PASS |

Backup lỗi: giữ log và file lỗi để điều tra, không dùng file chưa hoàn tất để restore. Checksum/giải mã lỗi: dừng, đối chiếu bản lưu và key được giao. Restore lỗi: giữ database đích để điều tra, không retry đè; dùng tên mới sau khi xác định nguyên nhân. Integrity hoặc số dòng không khớp: đánh dấu FAIL, giữ log và báo owner DB; không sửa ledger/audit để che sai lệch. Việc xóa database phục hồi sau khi lưu bằng chứng do người vận hành thực hiện riêng trên đúng cluster và đúng tên được duyệt.

Chỉ điền kết luận restore thành công sau khi có log và các kết quả thực tế nêu trên. Runbook và ví dụ lệnh không thay thế bằng chứng chạy.
