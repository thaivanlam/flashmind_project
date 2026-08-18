# Phát triển

## Yêu cầu

- Docker + Docker Compose (cách chạy được khuyến nghị)
- Hoặc chạy tay: JDK 25, Maven (**không có Maven wrapper** — dùng `mvn` của hệ thống),
  Node.js 20+, PostgreSQL 16, Redis 7
- Một OpenAI API key (chỉ cần cho chức năng sinh thẻ bằng AI)

## Chạy toàn bộ bằng Docker

```bash
cp .env.example .env          # rồi điền OPENAI_API_KEY
docker-compose up -d
docker-compose logs -f backend
```

| Dịch vụ | Cổng |
|---------|------|
| Frontend (nginx) | http://localhost:5173 |
| Backend | http://localhost:8080 |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |

Backend chờ postgres và redis healthy mới khởi động. Dữ liệu Postgres nằm trong volume
`pgdata`, nên `docker-compose down` không mất dữ liệu (`down -v` thì mất).

Chỉ dựng hạ tầng để phát triển local:

```bash
docker-compose up postgres redis -d
```

## Backend (`backend/`)

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run

mvn clean package -DskipTests
```

### Test

```bash
mvn test                                                       # toàn bộ
mvn test -Dtest=ReviewServiceTest                              # một lớp
mvn test -Dtest=ReviewServiceTest#firstReviewWithGoodQuality   # một phương thức
```

Hiện có 14 unit test Mockito trong `backend/src/test/java/com/flashmind/service/`
(`ReviewServiceTest` 9, `DeckServiceTest` 3, `FlashcardServiceTest` 2). Chúng chạy độc lập,
**không cần database hay Redis**. Không có test tích hợp và không có test controller.

## Frontend (`frontend/`)

```bash
npm install
npm run dev        # http://localhost:5173, proxy /api → localhost:8080
npm run build      # tsc -b && vite build
npm run preview    # xem thử bản build
```

### Lưu ý

- `npm run build` chạy `tsc -b` trước, và `tsconfig.json` bật `noUnusedLocals` +
  `noUnusedParameters` — **một import thừa cũng đủ làm hỏng build**.
- Kiểm tra kiểu mà không build: `npx tsc -b --noEmit`.
- `npm run lint` **không chạy được**: repo không cài eslint và không có file cấu hình eslint.
  Dùng lệnh kiểm tra kiểu ở trên, hoặc bổ sung eslint nếu thực sự cần lint.
- Không có test frontend và chưa có hạ tầng test nào được cấu hình.

## Thử nhanh bằng curl

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"123456","fullName":"Test User"}'

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"123456"}'
```

Lấy `accessToken` từ response rồi gọi tiếp:

```bash
curl http://localhost:8080/api/decks -H "Authorization: Bearer <accessToken>"
```

## Luồng dùng thử

1. Đăng ký tại `/register`
2. Tạo deck mới
3. Vào chi tiết deck → upload PDF/TXT → AI sinh thẻ
4. Vào **Ôn tập** → lật thẻ → chấm điểm 0–5
5. Vào **Thống kê** để xem streak và tiến độ

## Quy ước khi viết code

- Comment, log và thông báo lỗi cho người dùng viết bằng **tiếng Việt**;
  tên định danh và hợp đồng API bằng **tiếng Anh**.
- Controller không chứa nghiệp vụ; service nhận `userId` làm tham số, không đọc
  security context.
- Endpoint mới phải kiểm tra quyền qua `findDeckOwnedBy` / `findCardOwnedBy`
  (xem [backend.md](backend.md)).
- Mọi đường xóa phải tự dọn `card_reviews` (xem [data-model.md](data-model.md)).
- Thay đổi code thì cập nhật tài liệu tương ứng (xem [maintaining-docs.md](maintaining-docs.md)).
