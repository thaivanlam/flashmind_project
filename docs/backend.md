# Backend

Spring Boot 3.5.16 on Java 25, root package `com.flashmind`, entrypoint
[FlashmindApplication.java](../backend/src/main/java/com/flashmind/FlashmindApplication.java).

## Package structure

| Package | Role |
|---------|------|
| `config` | `SecurityConfig`, `RedisConfig`, `AnthropicClientConfig` |
| `controller` | 5 REST controllers, orchestration only — no business logic |
| `service` | All business logic, ownership checks, the SM-2 algorithm |
| `repository` | Spring Data JPA repositories |
| `entity` | 5 entities, no JPA relationships |
| `dto` | `dto.request` (with Bean Validation) and `dto.response` (with `from(...)` factories) |
| `exception` | 3 business exceptions + `GlobalExceptionHandler` |
| `security` | `JwtUtil`, `JwtAuthenticationFilter`, `UserPrincipal`, `AuthHelper` |

## Controllers

Controllers are a thin layer: take `userId` from `AuthHelper.getCurrentUserId()`, call the
service, return a `ResponseEntity`. No controller checks permissions itself.

| Controller | Base path |
|------------|-----------|
| [AuthController](../backend/src/main/java/com/flashmind/controller/AuthController.java) | `/api/auth` |
| [DeckController](../backend/src/main/java/com/flashmind/controller/DeckController.java) | `/api/decks` |
| [FlashcardController](../backend/src/main/java/com/flashmind/controller/FlashcardController.java) | `/api/decks/{deckId}/cards`, `/api/cards/{cardId}`, `/api/decks/{deckId}/generate-ai` |
| [ReviewController](../backend/src/main/java/com/flashmind/controller/ReviewController.java) | `/api/reviews` |
| [AnalyticsController](../backend/src/main/java/com/flashmind/controller/AnalyticsController.java) | `/api/analytics` |

Detailed specification: [api-reference.md](api-reference.md).

## Security

### JWT

[JwtUtil](../backend/src/main/java/com/flashmind/security/JwtUtil.java) signs with HS256 using
a key taken from `app.jwt.secret` **after Base64-decoding it**. Every token carries:

- `sub` — the user's email
- `userId` — the user's id
- `type` — `access` or `refresh`

Lifetimes: access **1 hour** (`app.jwt.access-expiration=3600000`), refresh **7 days**
(`app.jwt.refresh-expiration=604800000`).

`JwtAuthenticationFilter` accepts only `type = access`; `AuthService.refresh` accepts only
`type = refresh`. Invalid tokens are logged at WARN level and then ignored.

**Refresh tokens are not stored anywhere** — not in Redis, not in the database. As a result the
system currently has **no logout and no token revocation**; a token only stops working when it
expires.

### Filter chain

[SecurityConfig](../backend/src/main/java/com/flashmind/config/SecurityConfig.java):
CSRF disabled, `STATELESS` sessions, passwords hashed with `BCryptPasswordEncoder`.
Only `/api/auth/**` and `/actuator/**` are public, every other path requires authentication.
CORS takes its origins from `app.cors.allowed-origins`, allows `GET, POST, PUT, DELETE, OPTIONS`,
all headers, `allowCredentials = true`, `maxAge = 3600`.

### Authorization

```java
Deck deck = deckService.findDeckOwnedBy(deckId, userId);   // 404 if missing, 403 if owned by someone else
Flashcard card = flashcardService.findCardOwnedBy(cardId, userId); // ownership derived from the parent deck
```

`findCardOwnedBy` is `public` because `ReviewService` also uses it before writing to
`card_reviews` — that way an unowned card is rejected instead of silently creating a new
review row.

## Services

### AuthService

`register` rejects duplicate emails (`BusinessException` → 400), hashes the password and
returns a token pair. `login` returns the same message `"Email hoặc mật khẩu không đúng"` for
both a wrong email and a wrong password. `refresh` issues **both a new access and a new
refresh token**.

### DeckService

Deck CRUD plus two important helpers:

- `findDeckOwnedBy(deckId, userId)` — the single ownership checkpoint of the whole system.
- `updateCardCount(deckId)` — resynchronizes the denormalized `Deck.cardCount` column.
  **Call it after every card insert or delete.**

`deleteDeck` must clean up in exactly this order: collect the card ids → delete their
`card_reviews` → delete the flashcards → delete the deck. Reversing the order loses the ids and
leaves orphaned reviews behind (see [data-model.md](data-model.md)).

### FlashcardService

Card CRUD. `createCard` also creates a `CardReview` with `nextReviewDate = today` so a new card
shows up in the review list immediately. `deleteCard` deletes the review first and the card
after, then updates `cardCount`.

### ReviewService

- `getTodayReviews(userId)` — loads the due reviews, batch-loads the cards with `findAllById`,
  then zips them manually (there are no JPA relationships, so there is no automatic join).
  Reviews whose card could not be loaded are dropped, guaranteeing the client never receives
  `card: null`.
- `submitReview(cardId, userId, quality)` — checks ownership, applies SM-2, writes
  `lastReviewedAt`, updates today's `StudySession` (`quality >= 3` counts as correct) and
  returns the next review schedule.
- `applySpacedRepetition` — the **single source of truth** for the algorithm, detailed in
  [spaced-repetition.md](spaced-repetition.md). `MASTERY_THRESHOLD = 5` is duplicated in
  `AnalyticsService`; changing one means changing both.

### AnalyticsService

Aggregates `StudySession` rows over the last 30 days, filling empty days with zeros.

- `totalCardsReviewed` — **only sums those 30 days**, not all time.
- `masteredCards` — counts `CardReview` rows with `repetitionCount >= 5`.
- `currentStreak` — counts backwards from today; if today has no study yet, counting starts
  from yesterday, so the streak does not break until the day is over.

### AiGenerationService

The flow: check deck ownership → `FileParsingService.extractText` → build the prompt → call
Claude → save the cards → `updateCardCount`.

Claude is called through the official **Anthropic Java SDK** (`com.anthropic:anthropic-java`).
The `AnthropicClient` is a singleton bean built once in
[AnthropicClientConfig](../backend/src/main/java/com/flashmind/config/AnthropicClientConfig.java);
the call blocks on the request thread. Model `claude-opus-5`, `max_tokens` 16000, timeout
**120 seconds** — all configurable via `anthropic.*` properties. There is **no `temperature`**:
sampling parameters were removed on this model and sending one returns a 400. Adaptive thinking
is left on (the model's default) and must not be disabled.

The response shape is guaranteed by **structured outputs**, not by prompt text — the schema is
derived from the `GeneratedCards` / `GeneratedCard` records and enforced server-side, so the
service does no JSON shape-guessing and never touches an `ObjectMapper`. The static instructions
live in the `system` prompt; only the card count and file text go in the user message.

`stopReason` is checked **before** the content is read, because a refusal comes back as HTTP 200
with empty content:

| Condition | Message |
|-----------|---------|
| `stopReason = REFUSAL` | `The AI declined to process this file's content` |
| `stopReason = MAX_TOKENS` | `The AI response was too long, please request fewer cards` |
| `RateLimitException` (429) | `The AI is overloaded, please try again in a few minutes` |
| `NotFoundException` | bad model id → `Invalid AI configuration...` |

Cards missing `front` or `back` are skipped, and a blank `hint` is stored as `null`. Every
generated card comes with a `CardReview` due today. All failures surface as a
`BusinessException` → HTTP 400.

### FileParsingService

Accepts only `.pdf` (PDFBox) and `.txt` (UTF-8). An empty file or any other extension →
`BusinessException`. The text is **truncated to 8000 characters** to cap token spend. The upload
size limit is 5MB, set by `spring.servlet.multipart.max-file-size`.

### SchedulerService

| Cron | Method | What it does |
|------|--------|--------------|
| `0 0 0 * * *` | `cacheDailyDueCards` | Writes `due_cards:{userId}` to Redis, TTL 25 hours. **Nothing reads it yet.** |
| `0 0 3 * * *` | `cleanupOrphanedReviews` | Deletes `card_reviews` pointing at cards that no longer exist |

## Error handling

[GlobalExceptionHandler](../backend/src/main/java/com/flashmind/exception/GlobalExceptionHandler.java)
returns `{timestamp, status, message}` for every error:

| Exception | HTTP |
|-----------|------|
| `BusinessException` | 400 |
| `MethodArgumentNotValidException` (validation) | 400 |
| `ForbiddenException` | 403 |
| `ResourceNotFoundException` | 404 |
| `Exception` (anything else) | 500 |

Authorization failures must use `ForbiddenException` (→ 403), **not** `BusinessException`.

## Tests

14 Mockito unit tests in `backend/src/test/java/com/flashmind/service/`:

| Test class | Tests | Scope |
|------------|-------|-------|
| [ReviewServiceTest](../backend/src/test/java/com/flashmind/service/ReviewServiceTest.java) | 9 | The SM-2 math, plus 2 ownership cases for `submitReview` |
| [DeckServiceTest](../backend/src/test/java/com/flashmind/service/DeckServiceTest.java) | 3 | The deck delete path purges `card_reviews` in the right order |
| [FlashcardServiceTest](../backend/src/test/java/com/flashmind/service/FlashcardServiceTest.java) | 2 | The card delete path purges the review, and purges nothing when ownership fails |

No integration tests, no controller tests. How to run them: [development.md](development.md).
