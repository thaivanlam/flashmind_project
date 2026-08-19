# Medium findings

Eighteen findings, grouped by area so related fixes can travel together. These produce wrong
data, degrade under load, or expose infrastructure — none is immediately exploitable for a full
compromise, and none breaks the application outright.

See the [audit index](README.md) for scope, method and remediation order.

- [Analytics](#analytics) — M-1, M-2
- [Review and scheduling](#review-and-scheduling) — M-3, M-10, M-18
- [Data layer](#data-layer) — M-4, M-5, M-6, M-13
- [Uploads and AI](#uploads-and-ai) — M-7, M-8, M-16
- [Authentication](#authentication) — M-9, M-17
- [Infrastructure](#infrastructure) — M-11, M-12
- [Frontend](#frontend) — M-14, M-15

---

## Analytics

### M-1. `masteredCards` counts orphaned reviews

**Location:** [AnalyticsService.getUserAnalytics](../../backend/src/main/java/com/flashmind/service/AnalyticsService.java),
[CardReviewRepository](../../backend/src/main/java/com/flashmind/repository/CardReviewRepository.java)

**Description.** Every other read path defends against `card_reviews` rows whose card was
deleted — `findDueReviews` and `findDueCardIds` both filter on
`EXISTS (SELECT 1 FROM Flashcard f WHERE f.id = cr.cardId)`. But
`countByUserIdAndRepetitionCountGreaterThanEqual` is a plain derived count with no such filter,
so it counts reviews for cards that no longer exist and inflates the mastered-cards statistic.
This is the one gap in an otherwise consistent orphan defence.

**Trigger.** Master a card (5 repetitions), delete it, open the analytics page — it is still
counted. Any orphan row already in the database does the same.

**Fix.**

```java
@Query("SELECT COUNT(cr) FROM CardReview cr WHERE cr.userId = :userId " +
       "AND cr.repetitionCount >= :threshold " +
       "AND EXISTS (SELECT 1 FROM Flashcard f WHERE f.id = cr.cardId)")
long countMastered(@Param("userId") Long userId, @Param("threshold") int threshold);
```

---

### M-2. `totalCardsReviewed` is a 30-day figure presented as a lifetime total

**Location:** [AnalyticsService.getUserAnalytics](../../backend/src/main/java/com/flashmind/service/AnalyticsService.java),
[AnalyticsPage.tsx](../../frontend/src/pages/AnalyticsPage.tsx)

**Description.** `totalReviewed` sums only the sessions inside the 30-day window loaded for the
chart, but the DTO field is named `totalCardsReviewed` and the UI labels it as a total number of
reviews. A long-standing user's lifetime count silently shrinks as old sessions age out of the
window.

`calculateStreak` walks the same 30-entry map, so any streak longer than 30 days is reported as
exactly 30 — the number stops moving forever, which is precisely the point at which a streak
becomes motivating.

`calculateStreak` also returns 0 outright when today's session exists with `cardsReviewed == 0`.
The current write path cannot produce that row, but any future partial write would silently zero
the streak.

**Trigger.** Study daily for 31 days.

**Fix.** Query the totals independently of the chart window:

```java
@Query("SELECT COALESCE(SUM(s.cardsReviewed), 0) FROM StudySession s WHERE s.userId = :userId")
long sumAllCardsReviewed(@Param("userId") Long userId);

@Query("SELECT s.sessionDate FROM StudySession s WHERE s.userId = :userId " +
       "AND s.cardsReviewed > 0 ORDER BY s.sessionDate DESC")
List<LocalDate> findActiveDatesDesc(@Param("userId") Long userId);
```

and compute the streak over the full descending date list.

---

## Review and scheduling

### M-3. The same card can be reviewed unlimited times

**Location:** [ReviewService.submitReview](../../backend/src/main/java/com/flashmind/service/ReviewService.java)

**Description.** `submitReview` never checks that the card is actually due. Every call increments
`repetitionCount` and `cardsReviewed`, so a card reaches `MASTERY_THRESHOLD = 5` in five clicks
regardless of elapsed time, and the streak and total figures can be driven to any value. This is
also the practical trigger for the [H-2](high.md#h-2-sm-2-interval-overflows-int) overflow.

**Trigger.** `POST /api/reviews/{cardId}` in a loop, or a genuine double-submission.

**Fix.** Make the submission idempotent per day:

```java
if (review.getLastReviewedAt() != null
        && review.getLastReviewedAt().toLocalDate().isEqual(LocalDate.now())) {
    throw new BusinessException("This card has already been reviewed today");
}
```

---

### M-10. All scheduling uses the server's local date

**Location:** `LocalDate.now()` in
[ReviewService](../../backend/src/main/java/com/flashmind/service/ReviewService.java),
[AnalyticsService](../../backend/src/main/java/com/flashmind/service/AnalyticsService.java),
[FlashcardService](../../backend/src/main/java/com/flashmind/service/FlashcardService.java),
[AiGenerationService](../../backend/src/main/java/com/flashmind/service/AiGenerationService.java),
[SchedulerService](../../backend/src/main/java/com/flashmind/service/SchedulerService.java)

**Description.** `LocalDate.now()` resolves against the JVM's default zone. The backend container
sets no `TZ`, so it runs in UTC, while the frontend renders dates in the browser's zone. For a
user at UTC+7, cards become due and the daily study session rolls over at 07:00 local rather than
at midnight — so a streak can break even though the user studied every day — and the `0 0 0 * * *`
and `0 0 3 * * *` cron jobs fire mid-morning for them. No per-user timezone is stored anywhere.

**Trigger.** Any user outside the server's timezone.

**Fix.** Store a `zoneId` on `User`, defaulted from the browser at registration, resolve "today"
per user with `LocalDate.now(ZoneId.of(user.getZoneId()))`, and pass it into the services that
currently call `LocalDate.now()` themselves. As a stopgap, set `TZ` explicitly on the backend
container and document the single-timezone assumption in
[configuration.md](../configuration.md).

---

### M-18. `/api/reviews/today` is unpaginated

**Location:** [ReviewService.getTodayReviews](../../backend/src/main/java/com/flashmind/service/ReviewService.java)

**Description.** Every due review is returned in one response, each carrying its full card text.
A user returning after a long break with 20,000 due cards receives a multi-megabyte JSON payload
that the SPA loads entirely into Redux.

`flashcardRepository.findAllById(cardIds)` also builds a single `IN (…)` list. The PostgreSQL
wire protocol caps bind parameters at 65,535, so a large enough backlog fails outright.
`deleteByCardIdIn` in `DeckService.deleteDeck` shares the same ceiling for very large decks.

**Trigger.** Neglect reviews for a few months, or generate very large decks.

**Fix.** Add a bounded page — `findDueReviews(userId, date, PageRequest.of(0, 100))` — and have
the UI fetch the next batch when the current one is exhausted. Chunk the id lists before
`findAllById` and `deleteByCardIdIn`.

---

## Data layer

### M-4. `Deck.cardCount` drifts under concurrency

**Location:** [DeckService.updateCardCount](../../backend/src/main/java/com/flashmind/service/DeckService.java)

**Description.** `updateCardCount` reads `countByDeckId` and writes the result onto the deck.
Under `READ_COMMITTED`, two concurrent card creations each count 5 and each write 5 while the
true count is 6. No entity carries a `@Version` field, so nothing detects the lost update. The
denormalized counter then disagrees with the card list rendered on the deck detail page.

**Trigger.** Two card creations in the same deck concurrently — for instance an AI generation
running alongside a manual add.

**Fix.** Make it a single atomic statement so the count never round-trips through Java:

```java
@Modifying
@Query("UPDATE Deck d SET d.cardCount = " +
       "(SELECT COUNT(f) FROM Flashcard f WHERE f.deckId = d.id) WHERE d.id = :deckId")
void refreshCardCount(@Param("deckId") Long deckId);
```

Add `@Version private Long version;` to `Deck` and `CardReview` for defence in depth.

---

### M-5. `deleteByDeckId` is a derived delete

**Location:** [FlashcardRepository](../../backend/src/main/java/com/flashmind/repository/FlashcardRepository.java)

**Description.** Spring Data's derived `deleteByDeckId` loads every matching entity into the
persistence context and issues an individual `DELETE` per row. `DeckService.deleteDeck` already
uses a bulk `@Modifying` query for the reviews but not for the cards, so deleting a 5,000-card
deck runs 5,000 statements inside one transaction — a long lock hold and a request that can time
out.

**Trigger.** Delete a large deck. AI generation makes these easy to accumulate.

**Fix.**

```java
@Modifying
@Query("DELETE FROM Flashcard f WHERE f.deckId = :deckId")
int deleteByDeckId(@Param("deckId") Long deckId);
```

---

### M-6. No indexes on any of the hot query columns

**Location:** all entities under [entity/](../../backend/src/main/java/com/flashmind/entity/)

**Description.** The schema comes from `ddl-auto=update` and no entity declares `@Index`. With no
foreign keys either, PostgreSQL has nothing but the primary keys and the two unique constraints.
Every hot path is a sequential scan: `findDueReviews` (`user_id` plus `next_review_date` plus a
correlated `EXISTS` evaluated per row), `findByDeckIdOrderByCreatedAtAsc`,
`findByUserIdOrderByCreatedAtDesc`, `countByDeckId`, `deleteOrphanedReviews`.

`/api/reviews/today` therefore degrades with the size of the whole `card_reviews` table, not with
the user's share of it.

**Trigger.** Any dataset past a few thousand rows.

**Fix.**

```java
@Table(name = "card_reviews",
       uniqueConstraints = @UniqueConstraint(columnNames = {"card_id", "user_id"}),
       indexes = {
           @Index(name = "ix_review_user_due", columnList = "user_id, next_review_date"),
           @Index(name = "ix_review_card", columnList = "card_id")
       })
```

plus `flashcards(deck_id)` and `decks(user_id)`. `ddl-auto=update` does add new indexes, but
because of M-13 this should land as a migration rather than as an entity annotation alone.

---

### M-13. `ddl-auto=update` is the only schema mechanism

**Location:** [application.properties](../../backend/src/main/resources/application.properties)

**Description.** Hibernate's `update` is additive only. It never drops or alters a column, never
narrows a type, and will not retrofit a unique constraint onto a table that already contains
violating rows.

That last point is load-bearing. `CardReview` and `StudySession` rely on their unique constraints
for [H-3](high.md#h-3-study-session-and-card-review-upserts-are-read-modify-write-races); on a
database where data landed before the constraint, the constraint is silently absent and the races
produce silent duplicates instead of loud failures. There is also no rollback path and no record
of which schema a given release expects.

**Trigger.** Any schema change beyond adding a nullable column, or an upgrade over an existing
database.

**Fix.** Add Flyway, baseline the current schema as `V1__init.sql` — including the M-6 indexes and
explicit foreign keys where the manual-cascade contract allows them — and switch to
`spring.jpa.hibernate.ddl-auto=validate`.

---

## Uploads and AI

### M-7. Uploaded documents are silently truncated

**Location:** [FileParsingService.extractText](../../backend/src/main/java/com/flashmind/service/FileParsingService.java)

**Description.** Anything past `MAX_TEXT_LENGTH = 8000` characters is discarded with only a
server-side `log.info`. A 100-page PDF yields flashcards drawn from roughly its first two pages,
and the user is told the generation succeeded with no indication that most of their document was
never read. Silent truncation of user input is a correctness bug, not a cost optimisation.

**Trigger.** Upload any realistic study PDF.

**Fix.** Make truncation part of the API contract so the UI can report it — have `extractText`
return a record carrying the text, a `truncated` flag and the original length, and surface that
flag in the generate-AI response. Better still, chunk long documents and generate across chunks:
`claude-opus-5` has a 1M-token context window, so an 8,000-character cap is far more aggressive
than the model requires.

---

### M-8. File type is trusted from the filename extension alone

**Location:** [FileParsingService.extractText](../../backend/src/main/java/com/flashmind/service/FileParsingService.java)

**Description.** The only check is `filename.toLowerCase().endsWith(".pdf" | ".txt")`.
`MultipartFile.getContentType()` is ignored and no magic-byte check is performed. A 5 MB binary
renamed `notes.txt` is decoded as UTF-8 and shipped to Claude as garbage at full token cost; a
malformed or adversarial PDF named `.pdf` is fed to PDFBox — which has a history of
memory-exhaustion and parser vulnerabilities — on a request thread.

`Loader.loadPDF(file.getBytes())` also materialises the whole file in the heap, on top of the
buffer Tomcat already holds for the multipart request.

**Trigger.** `curl -F "file=@/dev/urandom;filename=x.txt"`, or an upload of a crafted PDF.

**Fix.** Validate the declared content type and the magic bytes, and stream rather than buffer:

```java
String contentType = file.getContentType();
if (!"application/pdf".equals(contentType) && !"text/plain".equals(contentType)) {
    throw new BusinessException("Only PDF and TXT files are supported");
}
// for PDF, also assert the first four bytes are %PDF before calling Loader.loadPDF
```

Use `Loader.loadPDF(new RandomAccessReadBuffer(file.getInputStream()))` to avoid the full
`byte[]`, and keep PDFBox patched.

---

### M-16. Nightly cache job is an N+1 writing keys nothing reads

**Location:** [SchedulerService.cacheDailyDueCards](../../backend/src/main/java/com/flashmind/service/SchedulerService.java)

**Description.** The job loads every distinct user id, then issues one `findDueCardIds` query per
user — each with the correlated `EXISTS` sub-select and no supporting index (M-6). At 100,000
users that is 100,000 sequential-scan queries on a single scheduler thread.

Nothing reads `due_cards:{userId}`. `/api/reviews/today` always queries PostgreSQL, so the entire
cost is waste plus Redis memory. On a multi-replica deployment every replica runs the job
simultaneously, with no shared lock.

The job also contains the one remaining untranslated log message in the backend:
`"Cache xong {} due cards cho {} users"`, which the repository-wide English convention requires
be rewritten.

**Trigger.** Midnight, every night, growing linearly with the user count.

**Fix.** Either delete the job and the `due_cards:*` scheme until there is a read path, or make it
useful: one grouped query for all users, a batched `MSET`, a read-through in
`ReviewService.getTodayReviews`, and a shared lock so only one replica runs it. Translate the log
message either way.

---

## Authentication

### M-9. Account enumeration, timing oracle, and no brute-force protection

**Location:** [AuthService.register](../../backend/src/main/java/com/flashmind/service/AuthService.java),
[AuthService.login](../../backend/src/main/java/com/flashmind/service/AuthService.java)

**Description.** Three issues in one flow:

1. `register` returns `"Email already exists"`, so anyone can test whether an address has an
   account.
2. `login` throws immediately when `findByEmail` is empty, skipping the BCrypt comparison
   entirely. An unknown email returns in about 1 ms; a known one takes about 100 ms at BCrypt
   cost 10. That is a trivially measurable enumeration oracle, even though the message itself is
   correctly generic.
3. There is no rate limiting, lockout or backoff on `/api/auth/login` — password guesses run
   unlimited and at full speed.

**Trigger.** Script a list of candidate addresses against either endpoint and time the responses.

**Fix.** Always perform a BCrypt comparison, against a dummy hash when the user is absent, so the
two paths cost the same:

```java
User user = userRepository.findByEmail(req.getEmail()).orElse(null);
String hash = (user != null) ? user.getPassword() : DUMMY_HASH;
boolean ok = passwordEncoder.matches(req.getPassword(), hash);
if (user == null || !ok) throw new BusinessException("Incorrect email or password");
```

Make `register` return the same generic response whether or not the address existed, using a
verification email to disambiguate, and add a Redis-backed attempt counter keyed on address and
client IP with exponential backoff.

---

### M-17. Registration race returns 500 instead of 409

**Location:** [AuthService.register](../../backend/src/main/java/com/flashmind/service/AuthService.java)

**Description.** `existsByEmail` followed by `save` is check-then-act. Two simultaneous
registrations for the same address both pass the check; the second hits the `users.email` unique
constraint and surfaces through the catch-all handler as a 500 carrying the raw constraint text
(see [H-5](high.md#h-5-the-catch-all-exception-handler-converts-framework-errors-into-500s)).

**Trigger.** Double-click the register button, or submit from two tabs.

**Fix.** Keep the pre-check for the friendly message, and treat the constraint violation as the
authoritative answer:

```java
try {
    user = userRepository.saveAndFlush(user);
} catch (DataIntegrityViolationException e) {
    throw new BusinessException("Email already exists");
}
```

---

## Infrastructure

### M-11. `/actuator/**` is fully public

**Location:** [SecurityConfig.filterChain](../../backend/src/main/java/com/flashmind/config/SecurityConfig.java)

**Description.** The whole actuator namespace is `permitAll()`. Boot's defaults expose only
`health` and `info` over HTTP, so the leak today is small — but the rule is written against
`/actuator/**`, so the moment anyone sets `management.endpoints.web.exposure.include=*`, a very
common debugging step, `/actuator/env`, `/actuator/configprops`, `/actuator/heapdump` and
`/actuator/loggers` become world-readable. `env` and `configprops` expose the datasource
password, the JWT secret and the Anthropic API key — masked by default, but `heapdump` is not.

**Trigger.** One property change, or an operator exposing metrics for Prometheus.

**Fix.** Permit only what must be anonymous, and pin the exposure list:

```java
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
.requestMatchers("/actuator/**").denyAll()
```

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never
```

---

### M-12. Compose publishes PostgreSQL and Redis with default/no authentication

**Location:** [docker-compose.yml](../../docker-compose.yml)

**Description.** PostgreSQL is published as `"5432:5432"` with `postgres/postgres`, and Redis as
`"6379:6379"` with no `requirepass` at all. Docker's port publishing writes its own iptables DNAT
rules and so bypasses host firewalls, meaning that on any machine with a public interface both
services are exposed to the internet. An unauthenticated Redis is a well-known path to remote
code execution through `CONFIG SET dir` and `dbfilename`.

**Trigger.** Running `docker-compose up -d` on anything other than a laptop behind NAT.

**Fix.** Drop the `ports:` blocks for `postgres` and `redis` entirely — the backend reaches both
over the compose network by service name. Where host access is genuinely needed for debugging,
bind to loopback (`"127.0.0.1:5432:5432"`). Supply `POSTGRES_PASSWORD` and a Redis `requirepass`
from environment variables with no defaults, and set `spring.data.redis.password` to match.

---

## Frontend

### M-14. `ReviewPage`'s timer is never cleaned up

**Location:** [ReviewPage.handleSubmit](../../frontend/src/pages/ReviewPage.tsx)

**Description.**

```ts
setTimeout(() => { dispatch(nextCard()); setLastResult(null); }, 1200);
```

There is no `useEffect` cleanup and no ref. Navigating away inside that window leaves the timer
armed, and it fires `nextCard()` against the Redux store after the component has unmounted.
Returning to `/review` re-fetches and resets `currentIndex` to 0 — but the stale timer's
`nextCard()` can land after the reset, advancing to index 1 and **silently skipping the first due
card of the new session**.

**Trigger.** Answer a card, click away within 1.2 seconds, then return to the review page.

**Fix.**

```ts
const timerRef = useRef<ReturnType<typeof setTimeout>>();
useEffect(() => () => clearTimeout(timerRef.current), []);
// in handleSubmit
timerRef.current = setTimeout(() => { dispatch(nextCard()); setLastResult(null); }, 1200);
```

---

### M-15. A non-numeric deck id produces `/decks/NaN` and a 500

**Location:** [DeckDetailPage](../../frontend/src/pages/DeckDetailPage.tsx)

**Description.** `const deckId = Number(id)` yields `NaN` for a URL such as `/decks/abc`. The API
client then requests `/api/decks/NaN`, which fails `@PathVariable Long` conversion and — because
of [H-5](high.md#h-5-the-catch-all-exception-handler-converts-framework-errors-into-500s) — comes
back as a 500 with a raw Spring message rather than a 400. The page catches it, shows a toast, and
then `if (!deck) return null` renders a completely blank page beneath the layout.

**Trigger.** Any hand-typed or stale bookmarked URL.

**Fix.** Guard before fetching, and render a real error state instead of nothing:

```ts
const deckId = Number(id);
if (!Number.isInteger(deckId) || deckId <= 0) return <Navigate to="/decks" replace />;
```

Replace `if (!deck) return null;` with a "deck not found" panel and a link back to the deck list.
