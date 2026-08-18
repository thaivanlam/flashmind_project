# API Reference

Base URL: `http://localhost:8080` (dev) — frontend luôn gọi qua tiền tố `/api`.

Mọi endpoint **trừ** `/api/auth/**` và `/actuator/**` đều yêu cầu header:

```
Authorization: Bearer <accessToken>
```

## Định dạng lỗi

Mọi lỗi đều trả về cùng một cấu trúc:

```json
{
  "timestamp": "2026-08-18T10:15:30.123",
  "status": 403,
  "message": "Không có quyền truy cập deck này"
}
```

| Mã | Khi nào |
|----|---------|
| 400 | Lỗi nghiệp vụ (`BusinessException`) hoặc validation thất bại |
| 401 / 403 | Thiếu token, token hỏng, token sai `type`, hoặc truy cập tài nguyên của người khác |
| 404 | Deck hoặc thẻ không tồn tại |
| 500 | Lỗi không lường trước |

---

## Authentication

### `POST /api/auth/register`

```json
{ "email": "test@test.com", "password": "123456", "fullName": "Test User" }
```

Ràng buộc: `email` đúng định dạng email, `password` 6–100 ký tự, `fullName` không rỗng.

Trả về `200` với `AuthResponse`:

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "user": { "id": 1, "email": "test@test.com", "fullName": "Test User", "createdAt": "2026-08-18T10:00:00" }
}
```

Email đã tồn tại → `400` `"Email đã tồn tại"`.

### `POST /api/auth/login`

```json
{ "email": "test@test.com", "password": "123456" }
```

Trả về `AuthResponse`. Sai email hoặc sai mật khẩu đều trả `400`
`"Email hoặc mật khẩu không đúng"` (cố ý không phân biệt).

### `POST /api/auth/refresh`

```json
{ "refreshToken": "eyJ..." }
```

Trả về `AuthResponse` với **cả access lẫn refresh token mới**. Token không hợp lệ hoặc
không mang `type = refresh` → `400` `"Refresh token không hợp lệ"`.

---

## Decks

Mọi endpoint deck đều giới hạn trong deck thuộc sở hữu của người gọi.

| Method | Path | Body | Trả về |
|--------|------|------|--------|
| `GET` | `/api/decks` | — | `DeckResponse[]`, mới nhất trước |
| `GET` | `/api/decks/{id}` | — | `DeckResponse` |
| `POST` | `/api/decks` | `DeckRequest` | `DeckResponse` |
| `PUT` | `/api/decks/{id}` | `DeckRequest` | `DeckResponse` |
| `DELETE` | `/api/decks/{id}` | — | `204 No Content` |

`DeckRequest`: `{ "title": "bắt buộc", "description": "tùy chọn", "language": "tùy chọn" }`

`DeckResponse`:

```json
{
  "id": 1, "userId": 1, "title": "TOEIC Vocabulary",
  "description": "…", "language": "en", "cardCount": 12,
  "createdAt": "2026-08-18T10:00:00"
}
```

`DELETE` xóa dây chuyền thủ công: review của các thẻ → các thẻ → deck.

---

## Flashcards

| Method | Path | Body | Trả về |
|--------|------|------|--------|
| `GET` | `/api/decks/{deckId}/cards` | — | `FlashcardResponse[]`, cũ nhất trước |
| `POST` | `/api/decks/{deckId}/cards` | `FlashcardRequest` | `FlashcardResponse` |
| `PUT` | `/api/cards/{cardId}` | `FlashcardRequest` | `FlashcardResponse` |
| `DELETE` | `/api/cards/{cardId}` | — | `204 No Content` |

`FlashcardRequest`: `{ "front": "bắt buộc", "back": "bắt buộc", "hint": "tùy chọn" }`

`FlashcardResponse`:

```json
{ "id": 7, "deckId": 1, "front": "…", "back": "…", "hint": null, "isAiGenerated": false }
```

Thẻ mới tạo kèm luôn một `CardReview` đến hạn **hôm nay**, và `Deck.cardCount` được cập nhật.

### `POST /api/decks/{deckId}/generate-ai`

`Content-Type: multipart/form-data`

| Tham số | Bắt buộc | Mô tả |
|---------|----------|-------|
| `file` | có | File `.pdf` hoặc `.txt`, tối đa 5MB |
| `count` | không | Số thẻ muốn sinh, mặc định `10` |

Trả về `FlashcardResponse[]` gồm các thẻ vừa được lưu (`isAiGenerated: true`), mỗi thẻ kèm
một `CardReview` đến hạn hôm nay.

Lỗi thường gặp, tất cả đều là `400`:

- `"Chỉ hỗ trợ file PDF và TXT"`
- `"File trống hoặc không thể đọc nội dung"`
- `"Không thể tạo flashcard từ AI: …"` — gọi OpenAI thất bại hoặc quá 60 giây
- `"AI trả về định dạng không hợp lệ"` / `"AI không sinh được flashcard nào"`

Nội dung file bị cắt còn 8000 ký tự trước khi gửi cho model.

---

## Reviews

### `GET /api/reviews/today`

Trả về mọi review có `nextReviewDate <= hôm nay` của người dùng, đã ghép sẵn nội dung thẻ:

```json
[
  {
    "id": 5, "cardId": 7,
    "card": { "id": 7, "deckId": 1, "front": "…", "back": "…", "hint": null, "isAiGenerated": true },
    "interval": 6, "easinessFactor": 2.5, "repetitionCount": 2,
    "nextReviewDate": "2026-08-18"
  }
]
```

Review nào không nạp được thẻ tương ứng đều bị loại, nên `card` trên thực tế không bao giờ null.

### `POST /api/reviews/{cardId}`

```json
{ "quality": 4 }
```

`quality` là số nguyên **0–5** (bắt buộc, có kiểm tra `@Min`/`@Max`).

```json
{ "nextReviewDate": "2026-08-24", "interval": 6, "isMastered": false }
```

Thẻ không tồn tại → `404`; thẻ của người khác → `403` (không có bản ghi nào được viết).
`isMastered` là `true` khi `repetitionCount >= 5`. Mỗi lần submit đều cập nhật
`StudySession` của hôm nay; `quality >= 3` được tính là trả lời đúng.

Công thức tính lịch: [spaced-repetition.md](spaced-repetition.md).

---

## Analytics

### `GET /api/analytics`

```json
{
  "currentStreak": 4,
  "totalCardsReviewed": 128,
  "masteredCards": 17,
  "last30Days": [ { "date": "2026-07-20", "cardsReviewed": 12, "correctCount": 9 } ]
}
```

- `last30Days` luôn có đúng 30 phần tử, ngày không học được lấp bằng 0.
- `totalCardsReviewed` **chỉ tính trong 30 ngày đó**, không phải tổng toàn thời gian.
- `masteredCards` đếm số thẻ có `repetitionCount >= 5`.
- `currentStreak` đếm lùi từ hôm nay; nếu hôm nay chưa học thì tính từ hôm qua.

---

## Actuator

`/actuator/**` là public (`spring-boot-starter-actuator`), dùng cho health check.
