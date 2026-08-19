# Security and correctness audit

A full-repository audit of FlashMind: bugs, logic errors, security vulnerabilities, data leaks
and performance bottlenecks. Style and naming were explicitly out of scope — the focus is
correctness, failure handling, state management and edge cases.

**Status: findings only. Nothing in this folder has been fixed yet.** Each finding describes the
code as it is today. As fixes land, move the finding to the "Fixed" table at the bottom of its
severity file, with the commit that fixed it.

## Documents

| Document | Contents |
|----------|----------|
| [critical.md](critical.md) | 3 findings — authentication bypass, broken session refresh, connection-pool exhaustion |
| [high.md](high.md) | 5 findings — uncapped AI spend, integer overflow, write races, token revocation, error handling |
| [medium.md](medium.md) | 18 findings — wrong analytics, missing indexes, upload validation, infrastructure exposure |
| [low.md](low.md) | 9 findings — client state hygiene, redundant parsing, documentation contradictions |

## Scope and method

This was a static review. No code was executed and no test was run against a live stack; every
finding is derived from reading the source.

Read in full:

- Every backend source file under [backend/src/main/java/com/flashmind/](../../backend/src/main/java/com/flashmind/)
  — controllers, services, repositories, entities, security, config, exceptions, DTOs.
- [application.properties](../../backend/src/main/resources/application.properties),
  [pom.xml](../../backend/pom.xml), [docker-compose.yml](../../docker-compose.yml),
  both Dockerfiles, [nginx.conf](../../frontend/nginx.conf), `.env.example`, `.gitignore`.
- Every frontend file under [frontend/src/](../../frontend/src/) — slices, API clients, pages,
  components, utilities, types.

The Anthropic Java SDK usage in
[AiGenerationService](../../backend/src/main/java/com/flashmind/service/AiGenerationService.java)
was checked against the current Claude API reference. **The API contract is correct**: the
structured-output call shape, the `claude-opus-5` model id, `max_tokens=16000` for a
non-streaming request, and the `stopReason`-before-content check all match the documented
patterns. The findings against that file concern transaction scope, input bounds and error
leakage — not the SDK call itself.

Two things worth stating because they were checked and found sound:

- **No XSS sinks.** There is no `dangerouslySetInnerHTML` anywhere; all user content renders
  through React's default escaping.
- **No IDOR.** The ownership model is applied consistently — `DeckService.findDeckOwnedBy` and
  `FlashcardService.findCardOwnedBy` guard every path that touches user data, including
  `ReviewService.submitReview`.

## All findings

| ID | Severity | Finding | Location |
|----|----------|---------|----------|
| [C-1](critical.md#c-1-publicly-known-jwt-signing-key-is-the-silent-production-default) | Critical | Publicly known JWT signing key is the silent production default | `application.properties`, `docker-compose.yml`, `.env.example` |
| [C-2](critical.md#c-2-expired-access-tokens-return-403-so-the-spa-never-refreshes) | Critical | Expired tokens return 403, so the SPA never refreshes — every session breaks after 1 hour | `SecurityConfig`, `axiosClient.ts` |
| [C-3](critical.md#c-3-the-claude-call-runs-inside-a-database-transaction) | Critical | The 120s Claude call runs inside a DB transaction — 10 concurrent uploads exhaust the pool | `AiGenerationService` |
| [H-1](high.md#h-1-count-is-unbounded-and-the-ai-endpoint-has-no-rate-limit) | High | `count` unbounded and no rate limit — uncapped Claude spend | `FlashcardController` |
| [H-2](high.md#h-2-sm-2-interval-overflows-int) | High | SM-2 interval overflows `int` → negative interval, card scheduled in the past | `ReviewService` |
| [H-3](high.md#h-3-study-session-and-card-review-upserts-are-read-modify-write-races) | High | Study-session and card-review upserts are read-modify-write races → 500s and lost reviews | `ReviewService` |
| [H-4](high.md#h-4-no-token-revocation) | High | No token revocation: 7-day refresh tokens in `localStorage`, logout is client-only | `AuthService`, `tokenStorage.ts` |
| [H-5](high.md#h-5-the-catch-all-exception-handler-converts-framework-errors-into-500s) | High | Catch-all `@ExceptionHandler(Exception.class)` turns framework errors into 500s and leaks internals | `GlobalExceptionHandler` |
| [M-1](medium.md#m-1-masteredcards-counts-orphaned-reviews) | Medium | `masteredCards` counts orphaned reviews | `AnalyticsService`, `CardReviewRepository` |
| [M-2](medium.md#m-2-totalcardsreviewed-is-a-30-day-figure-presented-as-a-lifetime-total) | Medium | `totalCardsReviewed` is a 30-day figure labelled a lifetime total; streak capped at 30 | `AnalyticsService` |
| [M-3](medium.md#m-3-the-same-card-can-be-reviewed-unlimited-times) | Medium | The same card can be reviewed unlimited times, inflating mastery and stats | `ReviewService` |
| [M-4](medium.md#m-4-deckcardcount-drifts-under-concurrency) | Medium | `Deck.cardCount` drifts under concurrency; no optimistic locking anywhere | `DeckService` |
| [M-5](medium.md#m-5-deletebydeckid-is-a-derived-delete) | Medium | `deleteByDeckId` is a derived delete → one statement per card | `FlashcardRepository` |
| [M-6](medium.md#m-6-no-indexes-on-any-of-the-hot-query-columns) | Medium | No indexes on any hot query column | all entities |
| [M-7](medium.md#m-7-uploaded-documents-are-silently-truncated) | Medium | Uploaded documents silently truncated to 8,000 characters | `FileParsingService` |
| [M-8](medium.md#m-8-file-type-is-trusted-from-the-filename-extension-alone) | Medium | File type trusted from the filename extension alone | `FileParsingService` |
| [M-9](medium.md#m-9-account-enumeration-timing-oracle-and-no-brute-force-protection) | Medium | Account enumeration, BCrypt timing oracle, no brute-force protection | `AuthService` |
| [M-10](medium.md#m-10-all-scheduling-uses-the-servers-local-date) | Medium | All scheduling uses the server's local date — wrong "today" outside the server timezone | 5 services |
| [M-11](medium.md#m-11-actuator-is-fully-public) | Medium | `/actuator/**` is fully public | `SecurityConfig` |
| [M-12](medium.md#m-12-docker-compose-publishes-postgres-and-redis-with-defaultno-authentication) | Medium | Compose publishes PostgreSQL and Redis to the host with default/no authentication | `docker-compose.yml` |
| [M-13](medium.md#m-13-ddl-autoupdate-is-the-only-schema-mechanism) | Medium | `ddl-auto=update` is the only schema mechanism | `application.properties` |
| [M-14](medium.md#m-14-reviewpages-timer-is-never-cleaned-up) | Medium | `ReviewPage`'s 1,200 ms timer is never cleaned up → a skipped card | `ReviewPage.tsx` |
| [M-15](medium.md#m-15-a-non-numeric-deck-id-produces-decksnan-and-a-500) | Medium | A non-numeric deck id produces `/decks/NaN` and a 500 | `DeckDetailPage.tsx` |
| [M-16](medium.md#m-16-nightly-cache-job-is-an-n1-writing-keys-nothing-reads) | Medium | Nightly cache job is an N+1 writing keys nothing reads | `SchedulerService` |
| [M-17](medium.md#m-17-registration-race-returns-500-instead-of-409) | Medium | Registration race returns 500 instead of 409 | `AuthService` |
| [M-18](medium.md#m-18-reviewstoday-is-unpaginated) | Medium | `/api/reviews/today` unpaginated; `findAllById` can exceed the bind-parameter limit | `ReviewService` |
| [L-1](low.md#l-1-concurrent-401s-each-fire-their-own-refresh) | Low | Concurrent 401s each fire their own refresh | `axiosClient.ts` |
| [L-2](low.md#l-2-isauthenticated-is-derived-from-the-cached-user) | Low | `isAuthenticated` derived from the cached user, not the token | `authSlice.ts` |
| [L-3](low.md#l-3-deck-slice-leaves-stale-state-and-swallows-write-failures) | Low | Deck slice leaves stale state and swallows write failures | `deckSlice.ts`, `DecksPage.tsx` |
| [L-4](low.md#l-4-every-request-parses-the-jwt-four-times) | Low | Every request parses the JWT four times and rebuilds the key each time | `JwtAuthenticationFilter`, `JwtUtil` |
| [L-5](low.md#l-5-documentation-contradicts-the-sm-2-implementation) | Low | Documentation contradicts the SM-2 implementation | `CLAUDE.md`, `spaced-repetition.md` |
| [L-6](low.md#l-6-container-hardening-and-logging-defaults) | Low | Container runs as root; `DEBUG` logging is the committed default | `Dockerfile`, `application.properties` |
| [L-7](low.md#l-7-cors-origins-are-not-trimmed) | Low | CORS origins not trimmed; `allowCredentials` unnecessary | `SecurityConfig` |
| [L-8](low.md#l-8-no-server-side-refusal-fallback-for-claude-opus-5) | Low | No server-side refusal fallback configured for `claude-opus-5` | `AiGenerationService` |
| [L-9](low.md#l-9-the-documented-language-convention-contradicts-claudemd) | Low | The documented language convention contradicts `CLAUDE.md` | `docs/README.md`, `maintaining-docs.md` |

Totals: **3 Critical, 5 High, 18 Medium, 9 Low — 35 findings.**

## Suggested remediation order

1. **C-1** — rotate the key and remove the defaults. Smallest change, largest exposure.
2. **C-2** — the 401 entry point. Unblocks the refresh flow; L-1 only becomes reachable after it.
3. **C-3** and **H-1** — the AI endpoint is the one place a single user can take the service down
   or run up an unbounded bill.
4. **H-5** — until the exception handler is fixed, many other findings surface as misleading
   500s, which makes them hard to test.
5. **H-2**, **H-3** and **M-3** together — one cluster around review submission, sharing
   `ReviewServiceTest` coverage.
6. **H-4** — token revocation is the largest design change here; schedule it deliberately.
7. The Medium findings by area: analytics (M-1, M-2), data layer (M-4, M-5, M-6, M-13), uploads
   (M-7, M-8), auth hygiene (M-9, M-17), infrastructure (M-11, M-12), frontend (M-14, M-15).

## Verification plan

| Finding | How to prove it is fixed |
|---------|--------------------------|
| C-1 | Start with `JWT_SECRET` unset → startup fails with a clear message. Start with a 16-byte secret → fails the length check. |
| C-2 | Issue a token with a 5-second expiry, wait, call `GET /api/decks` → **401**. Call another user's deck with a valid token → **403**. In the browser, the interceptor refreshes and the retried request succeeds. |
| C-3 | 15 concurrent `generate-ai` requests against a stubbed `AnthropicClient` that sleeps 60s; `GET /api/decks` stays responsive throughout, and Hikari active connections stay low. |
| H-1 | `count=0`, `count=-1`, `count=10000` → all 400. The 51st generation in a day → 429. |
| H-2 | New `ReviewServiceTest` case: 30 consecutive quality-5 submissions; assert `0 < interval <= MAX_INTERVAL_DAYS` and `nextReviewDate` is in the future. |
| H-3 | Two threads submitting the same user/card at once → both succeed or one gets 409; `study_sessions.cards_reviewed` equals the number of accepted submissions; no 500. |
| H-5 | `GET /api/decks/abc` → 400. `GET /api/nope` → 404. A 6 MB upload → 413. No body contains stack or SQL detail. |
| M-14 | Answer a card, navigate away within 1.2s, return to `/review` → the first due card is still shown. |
| Regression | `mvn test` (14 existing tests stay green) and `npx tsc -b --noEmit`. |

## Documentation to update as fixes land

Per [maintaining-docs.md](../maintaining-docs.md), the fixes in this audit will touch:

- [backend.md](../backend.md) — transaction boundaries (C-3), error mapping (H-5), auth status
  codes (C-2), the scheduler (M-16).
- [api-reference.md](../api-reference.md) — new status codes (H-5), `count` bounds (H-1),
  pagination (M-18), the truncation flag (M-7).
- [data-model.md](../data-model.md) — indexes (M-6), migrations (M-13), optimistic locking (M-4).
- [configuration.md](../configuration.md) and [development.md](../development.md) — secrets
  (C-1), exposed ports and Redis auth (M-12), actuator exposure (M-11).
- [frontend.md](../frontend.md) — the refresh interceptor (C-2, L-1), auth state (L-2).
- [spaced-repetition.md](../spaced-repetition.md) and `CLAUDE.md` — the interval ceiling (H-2)
  and the reset semantics (L-5).
