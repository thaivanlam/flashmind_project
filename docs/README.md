# Tài liệu FlashMind

Toàn bộ tài liệu của dự án nằm trong thư mục `docs/`. File này là mục lục — bắt đầu từ đây.

FlashMind là ứng dụng flashcard có AI sinh thẻ và lịch ôn tập theo thuật toán SM-2.
Backend Spring Boot 3.5.16 (Java 25) + frontend React 19/TypeScript/Vite, dữ liệu trên
PostgreSQL 16 và Redis 7, sinh thẻ bằng OpenAI `gpt-4o-mini`.

## Mục lục

| Tài liệu | Nội dung |
|----------|----------|
| [architecture.md](architecture.md) | Kiến trúc tổng thể, luồng dữ liệu, các quyết định thiết kế xuyên suốt |
| [backend.md](backend.md) | Phân tầng backend, từng service, bảo mật, scheduler, xử lý lỗi |
| [frontend.md](frontend.md) | Routing, Redux Toolkit, tầng API, lưu token, components |
| [api-reference.md](api-reference.md) | Đặc tả đầy đủ mọi endpoint: request, response, mã lỗi |
| [data-model.md](data-model.md) | Entity, bảng, ràng buộc, quy tắc xóa thủ công |
| [spaced-repetition.md](spaced-repetition.md) | Thuật toán SM-2 và cách nó được cài đặt |
| [development.md](development.md) | Chạy local, Docker, build, test |
| [configuration.md](configuration.md) | Biến môi trường, cổng, CORS, triển khai production |
| [maintaining-docs.md](maintaining-docs.md) | Thay đổi ở đâu thì phải cập nhật tài liệu nào |

## Quy ước

- Văn xuôi trong tài liệu, comment, log và thông báo lỗi cho người dùng viết bằng **tiếng Việt**;
  tên định danh và hợp đồng API bằng **tiếng Anh**.
- Mọi tài liệu mới đặt trong `docs/` và phải được thêm vào bảng mục lục ở trên.
- `README.md` ở thư mục gốc chỉ là trang giới thiệu ngắn, không phải nơi chứa tài liệu chi tiết.
- Khi code thay đổi, cập nhật tài liệu tương ứng trong cùng lần thay đổi đó
  (xem [maintaining-docs.md](maintaining-docs.md)).

## Trạng thái đã biết của dự án

Những điểm dưới đây là thực tế hiện tại của code, không phải kế hoạch:

- **Redis chỉ được ghi, chưa được đọc.** `/api/reviews/today` luôn truy vấn PostgreSQL.
- **Không có migration.** Schema do `spring.jpa.hibernate.ddl-auto=update` sinh ra.
- **Không có cơ chế logout / thu hồi token.** Refresh token là stateless.
- **Không có test frontend**, và script `npm run lint` không chạy được (thiếu eslint).
- **Database không có khóa ngoại.** Xóa dây chuyền được làm thủ công trong service.
