## Nội dung
<!-- FR / BR / SR liên quan, ví dụ: IN07, BR-08, IA-IN01 -->

## Checklist trước khi xin review
- [ ] Đã rebase lên `main` mới nhất, CI xanh
- [ ] Mọi endpoint mới có `@PreAuthorize(Authz.…)` (thiếu là app không khởi động)
- [ ] Mỗi thao tác có DTO request riêng (record), KHÔNG có trường status / createdBy / approvedBy / requesterId
- [ ] Kiểm tra chủ sở hữu (BR-20) trả `ApiException.notOwner(...)`, SoD trả `ApiException.sodViolation(...)`
- [ ] Đổi trạng thái chỉ qua endpoint hành động (`/submit`, `/approve`, `/confirm`…)
- [ ] Danh sách dùng `Paging.of(...)` + `PageResponse`
- [ ] Không sửa file trong `common/` hoặc migration đã merge (muốn sửa → PR riêng, báo Minh)
- [ ] Migration mới đặt tên đúng dải version của mình (xem CONTRIBUTING.md)
- [ ] Có test cho quy tắc nghiệp vụ / phân quyền chính
