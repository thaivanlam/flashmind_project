# Backend

Spring Boot 3.5.16 trên Java 25, package gốc `com.flashmind`, entrypoint
[FlashmindApplication.java](../backend/src/main/java/com/flashmind/FlashmindApplication.java).

## Cấu trúc package

| Package | Vai trò |
|---------|---------|
| `config` | `SecurityConfig`, `RedisConfig`, `WebClientConfig` |
| `controller` | 5 REST controller, chỉ điều phối — không chứa nghiệp vụ |
| `service` | Toàn bộ nghiệp vụ, kiểm tra quyền sở hữu, thuật toán SM-2 |
| `repository` | Spring Data JPA repository |
| `entity` | 5 entity, không có quan hệ JPA |
| `dto` | `dto.request` (có Bean Validation) và `dto.response` (có factory `from(...)`) |
| `exception` | 3 exception nghiệp vụ + `GlobalExceptionHandler` |
| `security` | `JwtUtil`, `JwtAuthenticationFilter`, `UserPrincipal`, `AuthHelper` |

## Controller

Controller là lớp mỏng: lấy `userId` từ `AuthHelper.getCurrentUserId()`, gọi service, trả
`ResponseEntity`. Không controller nào tự kiểm tra quyền.

| Controller | Base path |
|------------|-----------|
| [AuthController](../backend/src/main/java/com/flashmind/controller/AuthController.java) | `/api/auth` |
| [DeckController](../backend/src/main/java/com/flashmind/controller/DeckController.java) | `/api/decks` |
| [FlashcardController](../backend/src/main/java/com/flashmind/controller/FlashcardController.java) | `/api/decks/{deckId}/cards`, `/api/cards/{cardId}`, `/api/decks/{deckId}/generate-ai` |
| [ReviewController](../backend/src/main/java/com/flashmind/controller/ReviewController.java) | `/api/reviews` |
| [AnalyticsController](../backend/src/main/java/com/flashmind/controller/AnalyticsController.java) | `/api/analytics` |

Đặc tả chi tiết: [api-reference.md](api-reference.md).

## Bảo mật

### JWT

[JwtUtil](../backend/src/main/java/com/flashmind/security/JwtUtil.java) ký HS256 bằng khóa
lấy từ `app.jwt.secret` **sau khi giải Base64**. Mỗi token mang:

- `sub` — email người dùng
- `userId` — id người dùng
- `type` — `access` hoặc `refresh`

Hạn dùng: access **1 giờ** (`app.jwt.access-expiration=3600000`), refresh **7 ngày**
(`app.jwt.refresh-expiration=604800000`).

`JwtAuthenticationFilter` chỉ chấp nhận `type = access`; `AuthService.refresh` chỉ chấp nhận
`type = refresh`. Token không hợp lệ được log ở mức WARN rồi bỏ qua.

**Refresh token không được lưu ở đâu cả** — không Redis, không database. Vì vậy hệ thống
hiện **không có logout hay thu hồi token**; token chỉ hết hiệu lực khi hết hạn.

### Filter chain

[SecurityConfig](../backend/src/main/java/com/flashmind/config/SecurityConfig.java):
CSRF tắt, session `STATELESS`, mật khẩu băm bằng `BCryptPasswordEncoder`.
Chỉ `/api/auth/**` và `/actuator/**` là public, mọi đường dẫn khác yêu cầu xác thực.
CORS lấy origin từ `app.cors.allowed-origins`, cho phép `GET, POST, PUT, DELETE, OPTIONS`,
mọi header, `allowCredentials = true`, `maxAge = 3600`.

### Phân quyền

```java
Deck deck = deckService.findDeckOwnedBy(deckId, userId);   // 404 nếu không tồn tại, 403 nếu khác chủ
Flashcard card = flashcardService.findCardOwnedBy(cardId, userId); // quyền suy ra từ deck cha
```

`findCardOwnedBy` là `public` vì `ReviewService` cũng dùng nó trước khi ghi `card_reviews` —
nhờ vậy thẻ không thuộc sở hữu bị từ chối thay vì âm thầm tạo bản ghi review mới.

## Service

### AuthService

`register` chặn email trùng (`BusinessException` → 400), băm mật khẩu, trả về cặp token.
`login` trả cùng một thông báo `"Email hoặc mật khẩu không đúng"` cho cả hai trường hợp sai
email và sai mật khẩu. `refresh` cấp **cả access lẫn refresh token mới**.

### DeckService

CRUD deck cộng hai helper quan trọng:

- `findDeckOwnedBy(deckId, userId)` — điểm kiểm tra quyền duy nhất của toàn hệ thống.
- `updateCardCount(deckId)` — đồng bộ lại cột phi chuẩn hóa `Deck.cardCount`.
  **Gọi sau mọi thao tác thêm/xóa thẻ.**

`deleteDeck` phải dọn dữ liệu theo đúng thứ tự: lấy id các thẻ → xóa `card_reviews` của
chúng → xóa flashcards → xóa deck. Đảo thứ tự sẽ mất id và để lại review mồ côi
(xem [data-model.md](data-model.md)).

### FlashcardService

CRUD thẻ. `createCard` tạo kèm một `CardReview` với `nextReviewDate = hôm nay` để thẻ mới
xuất hiện ngay trong danh sách ôn. `deleteCard` xóa review trước rồi mới xóa thẻ, sau đó
cập nhật `cardCount`.

### ReviewService

- `getTodayReviews(userId)` — nạp review đến hạn, nạp thẻ theo lô bằng `findAllById`, rồi
  ghép thủ công (không có quan hệ JPA nên không có join tự động). Review nào không nạp được
  thẻ sẽ bị loại, đảm bảo client không bao giờ nhận `card: null`.
- `submitReview(cardId, userId, quality)` — kiểm tra quyền sở hữu, áp dụng SM-2, ghi
  `lastReviewedAt`, cập nhật `StudySession` của hôm nay (`quality >= 3` tính là đúng),
  trả về lịch ôn kế tiếp.
- `applySpacedRepetition` — **nguồn chân lý duy nhất** của thuật toán, chi tiết ở
  [spaced-repetition.md](spaced-repetition.md). `MASTERY_THRESHOLD = 5` được lặp lại
  trong `AnalyticsService`; sửa một nơi thì phải sửa cả hai.

### AnalyticsService

Tổng hợp `StudySession` trong 30 ngày gần nhất, lấp ngày trống bằng số 0.

- `totalCardsReviewed` — **chỉ cộng trong 30 ngày đó**, không phải tổng toàn thời gian.
- `masteredCards` — đếm `CardReview` có `repetitionCount >= 5`.
- `currentStreak` — đếm lùi từ hôm nay; nếu hôm nay chưa học thì bắt đầu đếm từ hôm qua,
  nên chuỗi không bị đứt cho tới hết ngày.

### AiGenerationService

Luồng: kiểm tra quyền deck → `FileParsingService.extractText` → dựng prompt → gọi OpenAI →
parse và lưu thẻ → `updateCardCount`.

Gọi OpenAI bằng `WebClient` (WebFlux) nhưng `.block()` — đồng bộ trong thread của request.
Tham số: `temperature 0.3`, `response_format: json_object`, timeout **60 giây**.

Bộ parse cố tình dễ dãi: chấp nhận mảng trần, khóa `flashcards`, khóa `cards`, hoặc trường
kiểu mảng đầu tiên tìm thấy. Thẻ thiếu `front` hoặc `back` bị bỏ qua. Mỗi thẻ sinh ra đều
kèm một `CardReview` đến hạn ngay hôm nay. Mọi thất bại đều nổi lên dưới dạng
`BusinessException` → HTTP 400.

### FileParsingService

Chỉ nhận `.pdf` (PDFBox) và `.txt` (UTF-8). File rỗng hoặc đuôi khác → `BusinessException`.
Văn bản bị **cắt còn 8000 ký tự** để giới hạn chi phí token. Giới hạn dung lượng upload là
5MB, đặt ở `spring.servlet.multipart.max-file-size`.

### SchedulerService

| Cron | Phương thức | Việc làm |
|------|-------------|----------|
| `0 0 0 * * *` | `cacheDailyDueCards` | Ghi `due_cards:{userId}` vào Redis, TTL 25 giờ. **Chưa có ai đọc.** |
| `0 0 3 * * *` | `cleanupOrphanedReviews` | Xóa `card_reviews` trỏ tới thẻ không còn tồn tại |

## Xử lý lỗi

[GlobalExceptionHandler](../backend/src/main/java/com/flashmind/exception/GlobalExceptionHandler.java)
trả về `{timestamp, status, message}` cho mọi lỗi:

| Exception | HTTP |
|-----------|------|
| `BusinessException` | 400 |
| `MethodArgumentNotValidException` (validation) | 400 |
| `ForbiddenException` | 403 |
| `ResourceNotFoundException` | 404 |
| `Exception` (còn lại) | 500 |

Lỗi phân quyền phải dùng `ForbiddenException` (→ 403), **không** dùng `BusinessException`.

## Test

14 unit test Mockito trong `backend/src/test/java/com/flashmind/service/`:

| Lớp test | Số test | Phạm vi |
|----------|---------|---------|
| [ReviewServiceTest](../backend/src/test/java/com/flashmind/service/ReviewServiceTest.java) | 9 | Toán SM-2, cộng 2 ca kiểm tra quyền sở hữu của `submitReview` |
| [DeckServiceTest](../backend/src/test/java/com/flashmind/service/DeckServiceTest.java) | 3 | Đường xóa deck dọn `card_reviews` đúng thứ tự |
| [FlashcardServiceTest](../backend/src/test/java/com/flashmind/service/FlashcardServiceTest.java) | 2 | Đường xóa thẻ dọn review, và không dọn gì khi sai quyền |

Không có test tích hợp, không có test controller. Cách chạy: [development.md](development.md).
