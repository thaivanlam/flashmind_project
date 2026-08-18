# Mô hình dữ liệu

PostgreSQL 16. Schema do `spring.jpa.hibernate.ddl-auto=update` sinh ra từ entity —
**không có Flyway/Liquibase, không có file migration**. Sửa entity chính là sửa schema;
thay đổi mang tính phá hủy (xóa cột, đổi kiểu) sẽ không được Hibernate áp dụng lên DB có sẵn.

## Bảng

### `users`

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | bigserial PK | |
| `email` | text | unique, not null |
| `password` | text | not null, băm BCrypt |
| `full_name` | text | |
| `created_at` | timestamp | not null, đặt trong `@PrePersist` |

### `decks`

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | bigserial PK | |
| `user_id` | bigint | not null, **không có FK** |
| `title` | text | not null |
| `description` | text | |
| `language` | varchar(10) | |
| `card_count` | int | **phi chuẩn hóa**, mặc định 0 |
| `created_at` | timestamp | not null |

### `flashcards`

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | bigserial PK | |
| `deck_id` | bigint | not null, **không có FK** |
| `front` | text | not null |
| `back` | text | not null |
| `hint` | text | |
| `is_ai_generated` | boolean | mặc định false |
| `created_at` | timestamp | not null |

### `card_reviews`

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | bigserial PK | |
| `card_id` | bigint | not null |
| `user_id` | bigint | not null |
| `interval_days` | int | mặc định 0 |
| `easiness_factor` | double | mặc định 2.5 |
| `repetition_count` | int | mặc định 0 |
| `next_review_date` | date | |
| `last_reviewed_at` | timestamp | |

Ràng buộc unique `(card_id, user_id)` — mỗi người dùng có tối đa một bản ghi lịch ôn cho mỗi thẻ.

### `study_sessions`

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | bigserial PK | |
| `user_id` | bigint | not null |
| `session_date` | date | not null |
| `cards_reviewed` | int | mặc định 0 |
| `correct_count` | int | mặc định 0 |

Ràng buộc unique `(user_id, session_date)` — mỗi người dùng một bản ghi mỗi ngày.

## Quan hệ (chỉ tồn tại về mặt logic)

```
users 1─* decks 1─* flashcards 1─1 card_reviews
users 1─* card_reviews
users 1─* study_sessions
```

**Không entity nào có `@ManyToOne` hay `@OneToMany`**, không cascade, và database không có
ràng buộc khóa ngoại. Toàn bộ quan hệ chỉ là các cột `Long`.

## Hệ quả bắt buộc phải nhớ

### 1. Join được viết tay

Không có lazy loading. Ví dụ `ReviewService.getTodayReviews` nạp review, gom `cardId`,
gọi `flashcardRepository.findAllById(...)` theo lô, rồi ghép hai danh sách trong bộ nhớ.

### 2. Xóa dây chuyền là thủ công — và có thứ tự

Mọi đường xóa đều **phải tự dọn `card_reviews`**.

```java
// DeckService.deleteDeck — đúng thứ tự
List<Long> cardIds = flashcardRepository.findIdsByDeckId(deckId); // 1. lấy id trước
cardReviewRepository.deleteByCardIdIn(cardIds);                   // 2. xóa review
flashcardRepository.deleteByDeckId(deckId);                       // 3. xóa thẻ
deckRepository.delete(deck);                                      // 4. xóa deck
```

```java
// FlashcardService.deleteCard
cardReviewRepository.deleteByCardId(cardId);  // review trước
flashcardRepository.delete(card);             // thẻ sau
```

Bỏ qua bước dọn review sẽ để lại **review mồ côi** — bản ghi `card_reviews` trỏ tới thẻ đã
biến mất. Triệu chứng: `/api/reviews/today` trả về `card: null` và giao diện ôn tập lỗi.

### 3. Ba lớp phòng thủ chống review mồ côi

Ngoài việc dọn đúng lúc xóa, hệ thống còn chặn dữ liệu mồ côi cũ ở nhiều tầng:

| Tầng | Cơ chế |
|------|--------|
| Truy vấn | `CardReviewRepository.findDueReviews` / `findDueCardIds` lọc bằng `EXISTS (… Flashcard …)` |
| Service | `ReviewService.getTodayReviews` loại review không nạp được thẻ |
| Định kỳ | `SchedulerService.cleanupOrphanedReviews` (cron `0 0 3 * * *`) xóa hẳn khỏi DB |
| Frontend | `reviewSlice` lọc `card == null`; `ReviewPage` không render `ReviewCard` khi thẻ null |

### 4. `Deck.cardCount` phải được đồng bộ tay

Sau **mọi** thao tác thêm hoặc xóa thẻ, gọi `DeckService.updateCardCount(deckId)`.
Hiện `FlashcardService.createCard`, `FlashcardService.deleteCard` và
`AiGenerationService.generateFromFile` đều đã gọi.

## Tài liệu liên quan

- Ý nghĩa các cột SM-2 trong `card_reviews`: [spaced-repetition.md](spaced-repetition.md)
- Nơi thi hành các quy tắc trên: [backend.md](backend.md)
