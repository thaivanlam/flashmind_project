# High findings

Five findings. None takes the service down on its own, but each causes data loss, unbounded
cost, or leaks internal detail to clients.

See the [audit index](README.md) for scope, method and remediation order.

---

## H-1. `count` is unbounded and the AI endpoint has no rate limit

| | |
|---|---|
| **Severity** | High |
| **Location** | [FlashcardController.generateFromAi](../../backend/src/main/java/com/flashmind/controller/FlashcardController.java) |

### Description

```java
@RequestParam(value = "count", defaultValue = "10") int count
```

There is no constraint on `count`, and `FlashcardController` is not annotated `@Validated` — so
even adding `@Min`/`@Max` to the parameter would be silently ignored. The value is interpolated
straight into the prompt (`"Extract %d key concepts…"`).

Nothing anywhere limits how often an authenticated user may call the endpoint. There is no
quota, no per-user counter, and no rate limiter in the filter chain. At `claude-opus-5` pricing
with `max_tokens=16000`, every call carries real cost, and a loop over the endpoint is an
unbounded billing drain — an authenticated-user financial denial of service.

`count=0` and `count=-5` produce a nonsensical prompt instead of a 400.

The frontend slider is bounded to 5–20
([AiGenerateForm.tsx](../../frontend/src/components/deck/AiGenerateForm.tsx)), but that is a UI
affordance, not a control — the endpoint accepts anything.

### Trigger

`POST /api/decks/1/generate-ai?count=100000` with any three-byte `.txt` file. Or simply a `for`
loop issuing the same perfectly valid request.

### Fix

Validate at the edge, and add a quota:

```java
@RestController
@Validated                       // required for @RequestParam constraints to be enforced
public class FlashcardController {

    @PostMapping(value = "/api/decks/{deckId}/generate-ai", consumes = "multipart/form-data")
    public ResponseEntity<List<FlashcardResponse>> generateFromAi(
            @PathVariable Long deckId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "count", defaultValue = "10") @Min(1) @Max(50) int count) { … }
}
```

Map `ConstraintViolationException` to 400 in `GlobalExceptionHandler`. For the quota, Redis is
already provisioned and currently unused (see
[M-16](medium.md#m-16-nightly-cache-job-is-an-n1-writing-keys-nothing-reads)): `INCR
gen_quota:{userId}` with a 24-hour TTL, rejecting over-quota requests with 429, fits the existing
infrastructure without adding a dependency.

---

## H-2. SM-2 interval overflows `int`

| | |
|---|---|
| **Severity** | High |
| **Location** | [ReviewService.applySpacedRepetition](../../backend/src/main/java/com/flashmind/service/ReviewService.java) |

### Description

```java
newInterval = (int) Math.round(r.getInterval() * r.getEasinessFactor());
```

`Math.round(double)` returns a `long`, and the cast to `int` silently wraps. With the easiness
factor at its 2.5 ceiling the interval grows 1 → 6 → 15 → 37 → 92 → … and crosses
`Integer.MAX_VALUE` at roughly the 24th consecutive quality-5 review (≈3.35 × 10⁹).

The wrapped value is negative, so `LocalDate.now().plusDays(negative)` schedules the card **in
the past**. The card becomes permanently due, forever, and `ReviewSubmitResponse.interval`
reports a negative number to the user. Values slightly beyond that instead make `plusDays` throw
`DateTimeException`, surfacing as a 500.

Twenty-four reviews is not "years of study". Because nothing prevents reviewing the same card
repeatedly ([M-3](medium.md#m-3-the-same-card-can-be-reviewed-unlimited-times)), it is twenty-four
clicks in one sitting.

### Trigger

Open a card in the review UI and answer 5 twenty-four times. The slower path is a genuinely
well-known card in a long-lived account.

### Fix

Compute in `long` and clamp to a sane ceiling:

```java
private static final int MAX_INTERVAL_DAYS = 365 * 10;   // 10 years

long raw = Math.round((long) r.getInterval() * r.getEasinessFactor());
newInterval = (int) Math.min(raw, MAX_INTERVAL_DAYS);
```

Per [maintaining-docs.md](../maintaining-docs.md), a change to `applySpacedRepetition` requires
updating [spaced-repetition.md](../spaced-repetition.md) **and** `ReviewServiceTest` — add a case
asserting the interval stays positive and bounded after 30 consecutive quality-5 submissions.

---

## H-3. Study-session and card-review upserts are read-modify-write races

| | |
|---|---|
| **Severity** | High |
| **Location** | [ReviewService.recordStudySession](../../backend/src/main/java/com/flashmind/service/ReviewService.java), [ReviewService.submitReview](../../backend/src/main/java/com/flashmind/service/ReviewService.java) |

### Description

Both methods follow the same pattern: `findBy…().orElseGet(() -> new …)` then `save()`.

`StudySession` carries a unique constraint on `(user_id, session_date)` and `CardReview` one on
`(card_id, user_id)`. Two concurrent submissions for the same user — or the same card — both
observe "absent", both insert, and the second violates the constraint. The resulting
`DataIntegrityViolationException` rolls back the whole `@Transactional submitReview`, so the
user's review is **discarded** and they receive a 500 (see
[H-5](#h-5-the-catch-all-exception-handler-converts-framework-errors-into-500s) for why it is a
500 rather than a 409).

Even without a constraint violation, the increments are lost updates. Two submissions each read
`cardsReviewed = 4` and both write `5`; one review vanishes from analytics.

The frontend `submitting` guard in
[ReviewPage](../../frontend/src/pages/ReviewPage.tsx) is client-side only and does not survive a
second tab or a second device.

### Trigger

Double-click a quality button, or review on two devices at once.

### Fix

Push the increment into the database as an atomic upsert:

```java
@Modifying
@Query(value = """
    INSERT INTO study_sessions (user_id, session_date, cards_reviewed, correct_count)
    VALUES (:userId, :date, 1, :correct)
    ON CONFLICT (user_id, session_date) DO UPDATE
      SET cards_reviewed = study_sessions.cards_reviewed + 1,
          correct_count  = study_sessions.correct_count + :correct
    """, nativeQuery = true)
void recordReview(@Param("userId") Long userId,
                  @Param("date") LocalDate date,
                  @Param("correct") int correct);
```

For `CardReview`, either catch `DataIntegrityViolationException` around the insert and retry the
read-then-update once, or add `@Version` optimistic locking to the entity.

This finding depends on the unique constraints actually existing in the database — see
[M-13](medium.md#m-13-ddl-autoupdate-is-the-only-schema-mechanism), which explains how
`ddl-auto=update` can leave them absent.

---

## H-4. No token revocation

| | |
|---|---|
| **Severity** | High |
| **Location** | [AuthService.refresh](../../backend/src/main/java/com/flashmind/service/AuthService.java), [tokenStorage.ts](../../frontend/src/utils/tokenStorage.ts), [authSlice.ts](../../frontend/src/store/authSlice.ts) |

### Description

Refresh is entirely stateless. `AuthService.refresh` validates the signature, checks the `type`
claim, loads the user, and issues a fresh pair. The old refresh token remains valid for its full
seven days — there is rotation, but no invalidation — and each refresh mints another seven-day
token. A single stolen refresh token therefore grants **indefinite** access.

`logout` only calls `clearTokens()` in the browser. There is no logout endpoint, no revocation
list, and no `password_changed_at` check, so neither the user nor an administrator can invalidate
an issued token by any means. Tokens live in `localStorage`, readable by any script running in
the origin.

The project documentation already records that refresh tokens are not stored in Redis. The point
of this finding is that the missing piece is a security control, not merely an unfinished
feature.

### Trigger

Any XSS, or a shared or stolen device. The victim pressing "log out" does not help.

### Fix

Give refresh tokens server-side state — Redis is already provisioned:

- On issue, store `refresh:{userId}:{jti}` with the refresh TTL, and put `jti` in the token.
- On refresh, require the key to exist, then delete it and issue a new `jti` — true rotation.
  A reused `jti` indicates theft: delete every key for that user.
- Add `POST /api/auth/logout` that deletes the caller's `jti` key.
- Move the refresh token into an `HttpOnly; Secure; SameSite=Strict` cookie so script cannot read
  it, keeping only the short-lived access token in memory.

The last point is what makes [L-7](low.md#l-7-cors-origins-are-not-trimmed)'s
`setAllowCredentials(true)` meaningful; today it is enabled without being needed.

---

## H-5. The catch-all exception handler converts framework errors into 500s

| | |
|---|---|
| **Severity** | High |
| **Location** | [GlobalExceptionHandler.handleGeneral](../../backend/src/main/java/com/flashmind/exception/GlobalExceptionHandler.java) |

### Description

`@ExceptionHandler(Exception.class)` in a `@RestControllerAdvice` outranks Spring MVC's own
handling for exceptions that already have well-defined status codes:

| Real situation | Correct status | Actually returned |
|---|---|---|
| `GET /api/decks/abc` (`MethodArgumentTypeMismatchException`) | 400 | **500** |
| Unknown URL (`NoResourceFoundException`) | 404 | **500** |
| Upload larger than 5 MB (`MaxUploadSizeExceededException`) | 413 | **500** |
| Malformed JSON body (`HttpMessageNotReadableException`) | 400 | **500** |
| Missing `file` parameter (`MissingServletRequestParameterException`) | 400 | **500** |
| Duplicate email insert (`DataIntegrityViolationException`) | 409 | **500** |

The body compounds it:

```java
return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Server error: " + ex.getMessage());
```

That hands the client raw internal detail — JDBC messages carrying table and column names and
SQLSTATE codes, constraint names, Hibernate diagnostics, file paths. An information-disclosure
leak on top of the wrong status code.

`AiGenerationService` repeats the pattern, returning
`"Could not generate flashcards from the AI: " + e.getMessage()` and so forwarding raw Anthropic
SDK error text — request ids, URLs, response fragments — to the browser.

### Trigger

Visit `/decks/abc` in the SPA (also reachable through
[M-15](medium.md#m-15-a-non-numeric-deck-id-produces-decksnan-and-a-500)), upload a 6 MB PDF, or
request any mistyped URL.

### Fix

Extend `ResponseEntityExceptionHandler` so framework exceptions keep their statuses, handle the
specific cases explicitly, and never echo `getMessage()` for an unknown error:

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "The file exceeds the 5MB limit");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        String ref = UUID.randomUUID().toString();
        log.error("Unhandled error ref={}", ref, ex);   // detail stays in the log
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error (reference: " + ref + ")");
    }
}
```

Apply the same treatment in `AiGenerationService`: log `e.getMessage()`, return a fixed string.

Because this handler currently masks the real status of several other findings, fixing it early
makes the rest of the audit testable — see the
[remediation order](README.md#suggested-remediation-order).
