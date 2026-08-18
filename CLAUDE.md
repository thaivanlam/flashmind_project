# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

FlashMind — AI flashcard app with SM-2 spaced repetition. Spring Boot 3.5.16 monolith (Java 25) + React 19/TypeScript/Vite SPA, backed by PostgreSQL 16 and Redis 7. Claude (`claude-opus-5`) generates flashcards from uploaded PDF/TXT.

**Write everything in English** — code comments, log messages, user-facing error strings, documentation, and commit messages, as well as identifiers and API contracts. Anything you add or edit must be English, including when you are modifying a file whose existing content is not.

The backend has been converted; the React frontend still contains Vietnamese UI text and is being migrated to English incrementally, so translate any frontend strings you touch rather than matching the surrounding language.

## Documentation

All project documentation lives in [docs/](docs/). [docs/README.md](docs/README.md) is the index.

**Before working on anything, read the docs covering the area you are about to touch.**
[docs/architecture.md](docs/architecture.md) and [docs/backend.md](docs/backend.md) /
[docs/frontend.md](docs/frontend.md) are the usual starting points; endpoint work needs
[docs/api-reference.md](docs/api-reference.md), data or delete paths need
[docs/data-model.md](docs/data-model.md).

**After changing code, update the matching documentation in the same change.**
[docs/maintaining-docs.md](docs/maintaining-docs.md) maps each code area to the docs that
must be updated. New docs go in `docs/`, and must be added to the index table in
`docs/README.md`.

The root `README.md` is a short landing page only — detailed documentation belongs in `docs/`.
This file (`CLAUDE.md`) and `docs/` must not contradict each other.

## Commands

### Full stack (Docker)
```bash
cp .env.example .env          # then set ANTHROPIC_API_KEY
docker-compose up -d          # frontend :5173, backend :8080, pg :5432, redis :6379
docker-compose logs -f backend
docker-compose up postgres redis -d   # infra only, for local dev
```

### Backend (`backend/`, no Maven wrapper — uses system `mvn`)
```bash
mvn spring-boot:run                                   # needs ANTHROPIC_API_KEY exported
mvn test                                              # all tests
mvn test -Dtest=ReviewServiceTest                     # one class
mvn test -Dtest=ReviewServiceTest#firstReviewWithGoodQuality   # one method
mvn clean package -DskipTests
```

### Frontend (`frontend/`)
```bash
npm install
npm run dev        # :5173, proxies /api → localhost:8080
npm run build      # tsc -b && vite build — type errors fail the build
```
`npm run lint` is declared but non-functional: neither `eslint` nor an eslint config exists in the repo. Use `npx tsc -b --noEmit` for the type check instead, or add the eslint toolchain if lint is actually needed.

Test coverage is 14 Mockito unit tests in `backend/src/test/java/com/flashmind/service/`: [ReviewServiceTest.java](backend/src/test/java/com/flashmind/service/ReviewServiceTest.java) (9 — the SM-2 math, plus two ownership cases asserting `submitReview` propagates `ForbiddenException` / `ResourceNotFoundException` and writes nothing), [DeckServiceTest.java](backend/src/test/java/com/flashmind/service/DeckServiceTest.java) (3) and [FlashcardServiceTest.java](backend/src/test/java/com/flashmind/service/FlashcardServiceTest.java) (2), both asserting delete paths purge `card_reviews` in the right order and purge nothing when ownership fails. No frontend tests, no test infra configured.

## Backend architecture

**Layering:** `controller` → `service` → `repository`. Controllers are thin: they call `AuthHelper.getCurrentUserId()` (reads `UserPrincipal` out of `SecurityContextHolder`) and pass `userId` down as an explicit argument. **Services never read the security context** — every service method that touches user data takes `userId` as a parameter. Preserve this when adding endpoints.

**Entities have no JPA relationships.** `Deck`, `Flashcard`, `CardReview`, `StudySession` store plain `Long` FK columns (`userId`, `deckId`, `cardId`) with no `@ManyToOne`/`@OneToMany`, no cascades, no DB-level FK constraints. Consequences to keep in mind:
- Joins are done manually in service code (see `ReviewService.getTodayReviews`, which batch-loads cards by id and zips them with reviews).
- Cascade deletion is manual and every delete path must do its own cleanup, `card_reviews` included. `DeckService.deleteDeck` collects card ids (`FlashcardRepository.findIdsByDeckId`), bulk-deletes their reviews, then deletes the flashcards and the deck — in that order, since the ids are gone once the cards are. `FlashcardService.deleteCard` deletes the card's review first. Skipping this leaves orphaned `card_reviews` rows, which surface as `card: null` in `/api/reviews/today` and crash the review UI.
- Defense in depth against orphans already in the DB: `CardReviewRepository.findDueReviews` / `findDueCardIds` filter on `EXISTS (… Flashcard …)`, `ReviewService.getTodayReviews` drops reviews whose card didn't load, and `SchedulerService.cleanupOrphanedReviews` (cron `0 0 3 * * *`) purges leftovers.
- `Deck.cardCount` is denormalized — call `DeckService.updateCardCount(deckId)` after any card insert/delete.

**Ownership authorization** is enforced entirely in the service layer via `DeckService.findDeckOwnedBy(deckId, userId)`, which throws `ForbiddenException` on mismatch (missing deck → `ResourceNotFoundException`). Card-level operations resolve ownership through the parent deck via the public `FlashcardService.findCardOwnedBy(cardId, userId)`; `ReviewService.submitReview` calls that same helper before touching `card_reviews`, so an unowned card is rejected rather than silently creating a fresh `CardReview` row. There is no `@PreAuthorize` anywhere — a new endpoint that skips these helpers has no access control.

**Auth:** stateless JWT, HS256 over a Base64-decoded secret (`app.jwt.secret`). Tokens carry a `type` claim (`access` / `refresh`); `JwtAuthenticationFilter` accepts only `access`, `AuthService.refresh` only `refresh`. Invalid tokens are logged and ignored (request continues unauthenticated → 401/403 from the filter chain). Access 1h, refresh 7d. Only `/api/auth/**` and `/actuator/**` are public. Despite the README, **refresh tokens are not stored in Redis** — refresh is stateless and there is no revocation/logout path.

**Redis** is currently write-only. `SchedulerService.cacheDailyDueCards` (cron `0 0 0 * * *`) writes `due_cards:{userId}` with a 25h TTL, but nothing reads those keys — `/api/reviews/today` always queries Postgres. Treat the cache as unfinished, not as a read path you can rely on.

**SM-2** lives in `ReviewService.applySpacedRepetition` — the single source of truth for scheduling. `quality < 3` resets `repetitionCount` and `interval` to 1; EF is clamped at 1.3; `MASTERY_THRESHOLD = 5` repetitions marks a card mastered (duplicated as a constant in `AnalyticsService`). Changing the algorithm means updating `ReviewServiceTest` alongside it.

**AI generation** (`AiGenerationService`): `FileParsingService` extracts text (PDFBox for PDF, UTF-8 for TXT; **truncated to 8000 chars** to cap token spend), then a blocking call to Claude through the official **Anthropic Java SDK** (`com.anthropic:anthropic-java`, version pinned by the `anthropic.version` property). The `AnthropicClient` is a singleton bean from `AnthropicClientConfig`; model `claude-opus-5`, `max_tokens` 16000, 120s timeout, all set via `anthropic.*` properties. **Never send `temperature` or any sampling parameter** — they were removed on this model and return a 400; likewise do not disable adaptive thinking. The response shape comes from **structured outputs** (schema derived from the `GeneratedCards`/`GeneratedCard` records and enforced server-side), so there is no tolerant parser and no `ObjectMapper`. `stopReason` must be checked before reading content — a refusal is HTTP 200 with empty content. All failures surface as `BusinessException`. Each generated card also gets a `CardReview` row with `nextReviewDate = today`.

**Errors:** `GlobalExceptionHandler` maps `BusinessException` → 400, `ForbiddenException` → 403, `ResourceNotFoundException` → 404, validation → 400, anything else → 500, all as `{timestamp, status, message}`. Ownership failures throw `ForbiddenException` and so surface as **403**; use it (not `BusinessException`) for any new authorization check.

**Schema** is managed by `spring.jpa.hibernate.ddl-auto=update`. There are no migrations (no Flyway/Liquibase) — entity edits are the schema change, and destructive changes won't be applied by Hibernate.

## Frontend architecture

Redux Toolkit with one slice per domain (`auth`, `deck`, `review`) in [src/store/](frontend/src/store/); each slice owns its `createAsyncThunk`s that delegate to a thin `*.api.ts` client. Components dispatch thunks — they don't call axios directly. `@/*` resolves to `src/*` (configured in both `tsconfig.json` and `vite.config.ts`).

[axiosClient.ts](frontend/src/api/axiosClient.ts) is the only HTTP entry point. Its response interceptor handles 401 by refreshing once (`_retry` guard, skipped for `/auth/*` URLs) and hard-redirects to `/login` on refresh failure. Base URL is `VITE_API_URL || '/api'` — dev goes through the Vite proxy, prod through nginx's `/api/` → `backend:8080` reverse proxy.

Tokens and the cached user live in `localStorage` behind [tokenStorage.ts](frontend/src/utils/tokenStorage.ts) (`flashmind_access_token` / `flashmind_refresh_token` / `flashmind_user`) — never touch `localStorage` keys directly. All routes except `/login` and `/register` are wrapped in `ProtectedRoute`.

TypeScript is strict with `noUnusedLocals` and `noUnusedParameters` on, and `tsc -b` runs as part of `npm run build`, so unused imports break the build.

## Configuration

All backend settings are env-overridable in [application.properties](backend/src/main/resources/application.properties): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET` (Base64), `ANTHROPIC_API_KEY` (required for AI generation). Defaults commit a dev JWT secret and assume localhost infra. Uploads cap at 5MB. `app.cors.allowed-origins` defaults to `http://localhost:5173,http://localhost:3000` — production deploys must override it.

## Edit protocol

Before every Edit or Write, explain in full: what the change does and why it is needed.
Do not batch several consecutive edits without explaining each one.
