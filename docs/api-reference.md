# API Reference

Base URL: `http://localhost:8080` (dev) — the frontend always calls through the `/api` prefix.

Every endpoint **except** `/api/auth/**` and `/actuator/**` requires the header:

```
Authorization: Bearer <accessToken>
```

## Error format

Every error returns the same structure:

```json
{
  "timestamp": "2026-08-18T10:15:30.123",
  "status": 403,
  "message": "Không có quyền truy cập deck này"
}
```

Error messages are returned in Vietnamese, exactly as the backend produces them.

| Code | When |
|------|------|
| 400 | Business error (`BusinessException`) or failed validation |
| 401 / 403 | Missing token, broken token, wrong token `type`, or accessing someone else's resource |
| 404 | The deck or card does not exist |
| 500 | Unexpected error |

---

## Authentication

### `POST /api/auth/register`

```json
{ "email": "test@test.com", "password": "123456", "fullName": "Test User" }
```

Constraints: `email` must be a valid email, `password` 6–100 characters, `fullName` not blank.

Returns `200` with an `AuthResponse`:

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "user": { "id": 1, "email": "test@test.com", "fullName": "Test User", "createdAt": "2026-08-18T10:00:00" }
}
```

An email that already exists → `400` `"Email đã tồn tại"`.

### `POST /api/auth/login`

```json
{ "email": "test@test.com", "password": "123456" }
```

Returns an `AuthResponse`. A wrong email and a wrong password both return `400`
`"Email hoặc mật khẩu không đúng"` (deliberately indistinguishable).

### `POST /api/auth/refresh`

```json
{ "refreshToken": "eyJ..." }
```

Returns an `AuthResponse` with **both a new access and a new refresh token**. A token that is
invalid or does not carry `type = refresh` → `400` `"Refresh token không hợp lệ"`.

---

## Decks

Every deck endpoint is limited to decks owned by the caller.

| Method | Path | Body | Returns |
|--------|------|------|---------|
| `GET` | `/api/decks` | — | `DeckResponse[]`, newest first |
| `GET` | `/api/decks/{id}` | — | `DeckResponse` |
| `POST` | `/api/decks` | `DeckRequest` | `DeckResponse` |
| `PUT` | `/api/decks/{id}` | `DeckRequest` | `DeckResponse` |
| `DELETE` | `/api/decks/{id}` | — | `204 No Content` |

`DeckRequest`: `{ "title": "required", "description": "optional", "language": "optional" }`

`DeckResponse`:

```json
{
  "id": 1, "userId": 1, "title": "TOEIC Vocabulary",
  "description": "…", "language": "en", "cardCount": 12,
  "createdAt": "2026-08-18T10:00:00"
}
```

`DELETE` cascades manually: the cards' reviews → the cards → the deck.

---

## Flashcards

| Method | Path | Body | Returns |
|--------|------|------|---------|
| `GET` | `/api/decks/{deckId}/cards` | — | `FlashcardResponse[]`, oldest first |
| `POST` | `/api/decks/{deckId}/cards` | `FlashcardRequest` | `FlashcardResponse` |
| `PUT` | `/api/cards/{cardId}` | `FlashcardRequest` | `FlashcardResponse` |
| `DELETE` | `/api/cards/{cardId}` | — | `204 No Content` |

`FlashcardRequest`: `{ "front": "required", "back": "required", "hint": "optional" }`

`FlashcardResponse`:

```json
{ "id": 7, "deckId": 1, "front": "…", "back": "…", "hint": null, "isAiGenerated": false }
```

A newly created card comes with a `CardReview` due **today**, and `Deck.cardCount` is updated.

### `POST /api/decks/{deckId}/generate-ai`

`Content-Type: multipart/form-data`

| Parameter | Required | Description |
|-----------|----------|-------------|
| `file` | yes | A `.pdf` or `.txt` file, at most 5MB |
| `count` | no | How many cards to generate, default `10` |

Returns a `FlashcardResponse[]` of the cards just saved (`isAiGenerated: true`), each with a
`CardReview` due today.

Common errors, all of them `400`:

- `"Chỉ hỗ trợ file PDF và TXT"`
- `"File trống hoặc không thể đọc nội dung"`
- `"Không thể tạo flashcard từ AI: …"` — the OpenAI call failed or exceeded 60 seconds
- `"AI trả về định dạng không hợp lệ"` / `"AI không sinh được flashcard nào"`

The file content is truncated to 8000 characters before being sent to the model.

---

## Reviews

### `GET /api/reviews/today`

Returns every review of the user with `nextReviewDate <= today`, with the card content already
zipped in:

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

Reviews whose card could not be loaded are dropped, so in practice `card` is never null.

### `POST /api/reviews/{cardId}`

```json
{ "quality": 4 }
```

`quality` is an integer **0–5** (required, checked with `@Min`/`@Max`).

```json
{ "nextReviewDate": "2026-08-24", "interval": 6, "isMastered": false }
```

A card that does not exist → `404`; someone else's card → `403` (and nothing is written).
`isMastered` is `true` when `repetitionCount >= 5`. Every submit also updates today's
`StudySession`; `quality >= 3` counts as a correct answer.

The scheduling formula: [spaced-repetition.md](spaced-repetition.md).

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

- `last30Days` always has exactly 30 entries; days without study are filled with zeros.
- `totalCardsReviewed` **only covers those 30 days**, not all time.
- `masteredCards` counts the cards with `repetitionCount >= 5`.
- `currentStreak` counts backwards from today; if today has no study yet, it starts from
  yesterday.

---

## Actuator

`/actuator/**` is public (`spring-boot-starter-actuator`), used for health checks.
