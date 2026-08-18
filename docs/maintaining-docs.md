# Bảo trì tài liệu

Tài liệu trong `docs/` là một phần của mã nguồn: thay đổi code và cập nhật tài liệu đi cùng
nhau trong một lần thay đổi, không để lại "sẽ cập nhật sau".

## Quy trình

**Trước khi bắt tay vào việc:** đọc [README.md](README.md) rồi mở những tài liệu liên quan
tới vùng sắp đụng tới.

**Sau khi thay đổi code:** tra bảng dưới đây, cập nhật mọi tài liệu bị ảnh hưởng.

## Ánh xạ code → tài liệu

| Bạn sửa gì | Cập nhật tài liệu nào |
|------------|------------------------|
| Thêm/sửa/xóa endpoint (controller, DTO, validation) | [api-reference.md](api-reference.md), [backend.md](backend.md); nếu client cũng đổi thì [frontend.md](frontend.md) |
| Entity, cột, ràng buộc, đường xóa dữ liệu | [data-model.md](data-model.md) |
| `ReviewService.applySpacedRepetition`, `MASTERY_THRESHOLD` | [spaced-repetition.md](spaced-repetition.md) **và** `ReviewServiceTest` |
| Service mới, đổi trách nhiệm service | [backend.md](backend.md) |
| Bảo mật: JWT, filter chain, quy tắc phân quyền | [backend.md](backend.md), [architecture.md](architecture.md); ảnh hưởng cấu hình thì [configuration.md](configuration.md) |
| Cron job, cách dùng Redis | [backend.md](backend.md), [architecture.md](architecture.md) |
| Prompt AI, parse file, giới hạn ký tự/kích thước | [backend.md](backend.md), [api-reference.md](api-reference.md) |
| Mã lỗi, `GlobalExceptionHandler` | [api-reference.md](api-reference.md), [backend.md](backend.md) |
| Route, slice Redux, tầng axios, lưu token | [frontend.md](frontend.md) |
| Interface TypeScript phản chiếu DTO | [frontend.md](frontend.md), [api-reference.md](api-reference.md) |
| `application.properties`, biến môi trường, `.env.example` | [configuration.md](configuration.md) |
| `docker-compose.yml`, Dockerfile, `nginx.conf` | [configuration.md](configuration.md), [development.md](development.md) |
| Script build/test, thêm hoặc bớt test | [development.md](development.md); số lượng test còn nêu ở [backend.md](backend.md) |
| Kiến trúc hoặc quyết định thiết kế xuyên suốt | [architecture.md](architecture.md), và cả `CLAUDE.md` ở thư mục gốc |

## Khi thêm tài liệu mới

1. Đặt file trong `docs/`, tên kebab-case, đuôi `.md`.
2. Thêm một dòng vào bảng mục lục trong [README.md](README.md).
3. Nếu nó mô tả một vùng code, thêm dòng tương ứng vào bảng ánh xạ ở trên.

## Nguyên tắc viết

- **Mô tả code như nó đang là, không như nó nên là.** Phần chưa hoàn thiện (Redis chỉ ghi,
  không có thu hồi token, không có migration) phải được ghi rõ là chưa hoàn thiện.
- Nêu con số cụ thể: cron, timeout, ngưỡng, giới hạn — đó là thứ người đọc tìm.
- Liên kết tới file nguồn bằng đường dẫn tương đối để người đọc nhảy thẳng vào code.
- Đừng chép nguyên khối code dài; chỉ trích phần thể hiện quy tắc.
- Văn xuôi tiếng Việt, định danh và hợp đồng API tiếng Anh.

## Quan hệ với `CLAUDE.md` và `README.md`

| File | Vai trò |
|------|---------|
| `docs/**` | Toàn bộ tài liệu chi tiết của dự án |
| `README.md` (gốc) | Trang giới thiệu ngắn cho GitHub, trỏ về `docs/` |
| `CLAUDE.md` (gốc) | Hướng dẫn dành riêng cho Claude Code, tóm tắt các bẫy kiến trúc |

Khi một quy tắc kiến trúc thay đổi, cả `docs/` và `CLAUDE.md` đều phải phản ánh — hai file
này không được mâu thuẫn nhau.
