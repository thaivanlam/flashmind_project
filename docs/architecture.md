# Kiến trúc tổng thể

## Thành phần

```
┌──────────────────────┐         ┌────────────────────────────┐
│  React 19 + TS SPA   │ ─HTTP─► │   Spring Boot 3 monolith   │
│  Redux Toolkit       │  /api   │   Spring Security (JWT)    │
│  Tailwind CSS        │         │   controller→service→repo  │
│  nginx (production)  │         └─────────────┬──────────────┘
└──────────────────────┘                       │
                    ┌──────────────────────────┼──────────────────────────┐
                    ▼                          ▼                          ▼
            ┌───────────────┐          ┌───────────────┐          ┌───────────────┐
            │ PostgreSQL 16 │          │    Redis 7    │          │  OpenAI API   │
            │  (dữ liệu)    │          │ (chỉ ghi cache│          │ (gpt-4o-mini) │
            └───────────────┘          │  due cards)   │          └───────────────┘
                                       └───────────────┘
```

Backend là **một monolith duy nhất**, không tách module. Frontend là SPA build tĩnh,
phục vụ qua nginx trong production và qua Vite dev server khi phát triển.

## Đường đi của một request

1. Trình duyệt gọi `/api/...`.
   - Dev: Vite proxy chuyển tiếp sang `http://localhost:8080`.
   - Production: nginx `location /api/` proxy sang `backend:8080`.
2. `JwtAuthenticationFilter` đọc header `Authorization: Bearer <token>`, xác thực chữ ký,
   yêu cầu claim `type = access`, rồi đặt `UserPrincipal` vào `SecurityContextHolder`.
   Token hỏng chỉ được ghi log và **bỏ qua** — request đi tiếp ở trạng thái chưa xác thực
   và bị filter chain chặn bằng 401/403.
3. Controller lấy `userId` qua `AuthHelper.getCurrentUserId()` và truyền xuống service
   như một tham số tường minh.
4. Service kiểm tra quyền sở hữu, thực thi nghiệp vụ, gọi repository.
5. Lỗi ném ra được `GlobalExceptionHandler` chuyển thành JSON `{timestamp, status, message}`.

## Các quyết định thiết kế xuyên suốt

### Service không đọc security context

`AuthHelper` chỉ được gọi trong tầng controller. Mọi phương thức service chạm tới dữ liệu
người dùng đều nhận `userId` làm tham số. Điều này giữ cho service test được bằng Mockito
thuần và không phụ thuộc vào Spring Security. **Giữ nguyên quy ước này khi thêm endpoint mới.**

### Entity không có quan hệ JPA

`Deck`, `Flashcard`, `CardReview`, `StudySession` chỉ lưu cột khóa ngoại kiểu `Long`
(`userId`, `deckId`, `cardId`) — không `@ManyToOne`/`@OneToMany`, không cascade,
không ràng buộc FK ở tầng database. Hệ quả và cách xử lý xem [data-model.md](data-model.md).

### Phân quyền nằm ở tầng service

Không có `@PreAuthorize` ở bất kỳ đâu. Quyền sở hữu được kiểm tra bằng
`DeckService.findDeckOwnedBy(deckId, userId)` và `FlashcardService.findCardOwnedBy(cardId, userId)`.
Endpoint mới nào bỏ qua hai helper này là **không có kiểm soát truy cập**.

### Redis chưa phải đường đọc

`SchedulerService.cacheDailyDueCards` ghi key `due_cards:{userId}` (TTL 25 giờ), nhưng
không có code nào đọc lại. Coi đây là phần chưa hoàn thiện, đừng dựa vào nó như một cache thật.

### Schema do Hibernate sinh

`spring.jpa.hibernate.ddl-auto=update`, không có Flyway/Liquibase. Sửa entity **chính là**
sửa schema; các thay đổi mang tính phá hủy (xóa cột, đổi kiểu) sẽ không được Hibernate áp dụng.

## Tài liệu liên quan

- Chi tiết từng lớp backend: [backend.md](backend.md)
- Chi tiết SPA: [frontend.md](frontend.md)
- Hợp đồng API: [api-reference.md](api-reference.md)
