# Data model

PostgreSQL 16. The schema is generated from the entities by
`spring.jpa.hibernate.ddl-auto=update` — **no Flyway/Liquibase, no migration files**. Editing an
entity is the schema change; destructive changes (dropping a column, changing a type) will not
be applied by Hibernate to an existing database.

## Tables

### `users`

| Column | Type | Notes |
|--------|------|-------|
| `id` | bigserial PK | |
| `email` | text | unique, not null |
| `password` | text | not null, BCrypt hash |
| `full_name` | text | |
| `created_at` | timestamp | not null, set in `@PrePersist` |

### `decks`

| Column | Type | Notes |
|--------|------|-------|
| `id` | bigserial PK | |
| `user_id` | bigint | not null, **no FK** |
| `title` | text | not null |
| `description` | text | |
| `language` | varchar(10) | |
| `card_count` | int | **denormalized**, default 0 |
| `created_at` | timestamp | not null |

### `flashcards`

| Column | Type | Notes |
|--------|------|-------|
| `id` | bigserial PK | |
| `deck_id` | bigint | not null, **no FK** |
| `front` | text | not null |
| `back` | text | not null |
| `hint` | text | |
| `is_ai_generated` | boolean | default false |
| `created_at` | timestamp | not null |

### `card_reviews`

| Column | Type | Notes |
|--------|------|-------|
| `id` | bigserial PK | |
| `card_id` | bigint | not null |
| `user_id` | bigint | not null |
| `interval_days` | int | default 0 |
| `easiness_factor` | double | default 2.5 |
| `repetition_count` | int | default 0 |
| `next_review_date` | date | |
| `last_reviewed_at` | timestamp | |

Unique constraint `(card_id, user_id)` — each user has at most one schedule row per card.

### `study_sessions`

| Column | Type | Notes |
|--------|------|-------|
| `id` | bigserial PK | |
| `user_id` | bigint | not null |
| `session_date` | date | not null |
| `cards_reviewed` | int | default 0 |
| `correct_count` | int | default 0 |

Unique constraint `(user_id, session_date)` — one row per user per day.

## Relationships (logical only)

```
users 1─* decks 1─* flashcards 1─1 card_reviews
users 1─* card_reviews
users 1─* study_sessions
```

**No entity has `@ManyToOne` or `@OneToMany`**, there are no cascades, and the database has no
foreign key constraints. Every relationship is just a `Long` column.

## Consequences you must remember

### 1. Joins are written by hand

There is no lazy loading. For example `ReviewService.getTodayReviews` loads the reviews, collects
the `cardId`s, calls `flashcardRepository.findAllById(...)` as a batch, then zips the two lists
in memory.

### 2. Cascade deletion is manual — and ordered

Every delete path **must clean up `card_reviews` itself**.

```java
// DeckService.deleteDeck — the correct order
List<Long> cardIds = flashcardRepository.findIdsByDeckId(deckId); // 1. collect the ids first
cardReviewRepository.deleteByCardIdIn(cardIds);                   // 2. delete the reviews
flashcardRepository.deleteByDeckId(deckId);                       // 3. delete the cards
deckRepository.delete(deck);                                      // 4. delete the deck
```

```java
// FlashcardService.deleteCard
cardReviewRepository.deleteByCardId(cardId);  // the review first
flashcardRepository.delete(card);             // the card after
```

Skipping the review cleanup leaves **orphaned reviews** — `card_reviews` rows pointing at a card
that is gone. The symptom: `/api/reviews/today` returns `card: null` and the review UI breaks.

### 3. Three layers of defence against orphaned reviews

Beyond cleaning up at delete time, the system also blocks pre-existing orphans at several layers:

| Layer | Mechanism |
|-------|-----------|
| Query | `CardReviewRepository.findDueReviews` / `findDueCardIds` filter with `EXISTS (… Flashcard …)` |
| Service | `ReviewService.getTodayReviews` drops reviews whose card did not load |
| Scheduled | `SchedulerService.cleanupOrphanedReviews` (cron `0 0 3 * * *`) deletes them from the DB |
| Frontend | `reviewSlice` filters `card == null`; `ReviewPage` does not render `ReviewCard` for a null card |

### 4. `Deck.cardCount` must be synchronized by hand

After **every** card insert or delete, call `DeckService.updateCardCount(deckId)`.
Today `FlashcardService.createCard`, `FlashcardService.deleteCard` and
`AiGenerationService.generateFromFile` all call it.

## Related documents

- What the SM-2 columns in `card_reviews` mean: [spaced-repetition.md](spaced-repetition.md)
- Where the rules above are enforced: [backend.md](backend.md)
