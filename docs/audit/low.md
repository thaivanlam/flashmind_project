# Low findings

Nine findings. Real defects with limited blast radius: client-side state hygiene, redundant work
on hot paths, deployment hardening, and two places where the documentation contradicts the code.

See the [audit index](README.md) for scope, method and remediation order.

---

## L-1. Concurrent 401s each fire their own refresh

**Location:** [axiosClient.ts](../../frontend/src/api/axiosClient.ts)

**Description.** The dashboard issues three requests in parallel. If the access token has just
expired, all three enter the refresh branch and issue three separate `POST /auth/refresh` calls
whose `setTokens` results race — last write wins. Because refresh is stateless
([H-4](high.md#h-4-no-token-revocation)) every result is individually valid, so the damage today
is wasted requests rather than a broken session; once refresh becomes stateful with rotation, the
same race would invalidate the winning token.

This branch is currently unreachable because of
[C-2](critical.md#c-2-expired-access-tokens-return-403-so-the-spa-never-refreshes) — fixing C-2
makes it live.

Separately, the `catch` block falls through to `return Promise.reject(error)`, so callers display
error toasts while the page is already navigating to `/login`.

**Fix.** Share one in-flight refresh across all callers with a module-level
`let refreshPromise: Promise<…> | null`, cleared in `finally`, that every 401 awaits. In the
failure path, return a never-resolving promise instead of rejecting, so no toast fires during the
redirect.

---

## L-2. `isAuthenticated` is derived from the cached user

**Location:** [authSlice.ts](../../frontend/src/store/authSlice.ts)

**Description.** `isAuthenticated: !!getStoredUser<User>()` keys authentication off the cached
user object rather than the token. A stale `flashmind_user` entry with no tokens alongside it
renders the entire protected application, which then fails every request. This is what makes
[C-2](critical.md#c-2-expired-access-tokens-return-403-so-the-spa-never-refreshes) present as a
broken app rather than as a redirect to the login page.

The initial state also calls `getStoredUser` twice, parsing the same JSON on both lines.

**Fix.** Derive the flag from `!!getAccessToken()`, ideally checking the token's `exp` claim, and
hoist the stored user into a single `const`.

---

## L-3. Deck slice leaves stale state and swallows write failures

**Location:** [deckSlice.ts](../../frontend/src/store/deckSlice.ts),
[DecksPage.tsx](../../frontend/src/pages/DecksPage.tsx)

**Description.** `deleteDeck.fulfilled` filters `state.decks` but never clears `state.current`, so
the detail view retains a deck that no longer exists.

`createDeck`, `updateDeck` and `deleteDeck` have no `rejectWithValue` and no `rejected` reducers.
`DecksPage.handleCreate` checks `createDeck.fulfilled.match(result)` and silently does nothing
when it fails — the modal simply stays open with no message, and the user has no idea whether
anything happened. `fetchDecks.rejected` does set `state.error`, but no component ever reads it.

**Fix.** Add `rejected` cases to the slice with `rejectWithValue` in the thunks, surface the error
through toasts in the pages, and clear `state.current` on successful deletion.

---

## L-4. Every request parses the JWT four times

**Location:** [JwtAuthenticationFilter.doFilterInternal](../../backend/src/main/java/com/flashmind/security/JwtAuthenticationFilter.java),
[JwtUtil.getSigningKey](../../backend/src/main/java/com/flashmind/security/JwtUtil.java)

**Description.** The filter calls `validateToken`, `extractType`, `extractUserId` and
`extractEmail` — four full `parseSignedClaims` invocations, each performing HMAC verification.
Every one of them calls `getSigningKey()`, which re-runs `Base64.getDecoder().decode(secret)` and
allocates a new `SecretKeySpec`. That is four signature verifications and four key derivations on
every authenticated request.

**Fix.** Parse once and read every claim off the returned `Claims`, and cache the key in
`@PostConstruct` as part of [C-1](critical.md#c-1-publicly-known-jwt-signing-key-is-the-silent-production-default).

---

## L-5. Documentation contradicts the SM-2 implementation

**Location:** `CLAUDE.md`, [spaced-repetition.md](../spaced-repetition.md) vs
[ReviewService.applySpacedRepetition](../../backend/src/main/java/com/flashmind/service/ReviewService.java)

**Description.** `CLAUDE.md` states that `quality < 3` "resets `repetitionCount` and `interval` to
1". The code sets `repetitionCount` to **0** and `interval` to 1:

```java
r.setRepetitionCount(0);
r.setInterval(1);
```

The distinction matters, because `repetitionCount` drives both the next interval (`0` → 1 day,
`1` → 6 days) and the mastery threshold. A reader implementing against the documented behaviour
would produce a different schedule.

**Fix.** Correct the sentence to "resets `repetitionCount` to 0 and `interval` to 1" in both
`CLAUDE.md` and [spaced-repetition.md](../spaced-repetition.md), and verify the two agree with
`ReviewServiceTest`.

---

## L-6. Container hardening and logging defaults

**Location:** [backend/Dockerfile](../../backend/Dockerfile),
[application.properties](../../backend/src/main/resources/application.properties),
[docker-compose.yml](../../docker-compose.yml)

**Description.** The backend image runs as root — there is no `USER` directive — and the backend
service has no healthcheck in compose, although PostgreSQL and Redis both do, so
`depends_on: backend` for the frontend only waits for the container to start rather than for the
application to become ready.

`logging.level.com.flashmind=DEBUG` is the committed default, which is noisy in production and
raises the chance of sensitive values reaching the logs.

**Fix.** Add a non-root user to the runtime stage (`addgroup -S app && adduser -S app -G app`,
then `USER app`), add a healthcheck hitting `/actuator/health`, and default the log level to
`INFO` with `DEBUG` available through an environment override.

---

## L-7. CORS origins are not trimmed

**Location:** [SecurityConfig.corsConfigurationSource](../../backend/src/main/java/com/flashmind/config/SecurityConfig.java)

**Description.** `Arrays.asList(allowedOrigins.split(","))` preserves surrounding whitespace, so a
perfectly natural value such as `"http://a.com, http://b.com"` silently fails to match the second
origin. The failure mode is a CORS error in the browser with nothing wrong in the logs.

`setAllowCredentials(true)` is also unnecessary: authentication travels in the `Authorization`
header, not in cookies.

**Fix.** Trim each entry with `.map(String::trim)`. Drop `setAllowCredentials(true)` unless the
cookie-based refresh token from [H-4](high.md#h-4-no-token-revocation) is adopted, in which case
keep it and keep the origin list strict.

---

## L-8. No server-side refusal fallback for `claude-opus-5`

**Location:** [AiGenerationService.callClaude](../../backend/src/main/java/com/flashmind/service/AiGenerationService.java)

**Description.** The refusal path itself is handled correctly: `stopReason` is checked before the
content is read, and `stopDetails` is only consulted on a refusal — both match the current API
contract, since a refusal arrives as HTTP 200 with empty content.

What is missing is the recommended default for this model: the server-side `fallbacks` parameter,
which routes a refused request to a fallback model by refusal category instead of failing the
user's upload outright. Without it, an over-eager classifier on a legitimate study document
returns "The AI declined to process this file's content" and the upload is simply lost.

**Fix.** Add the beta flag `server-side-fallback-2026-07-01` with `fallbacks: "default"` so
refusals degrade gracefully rather than surfacing as a failed generation.

---

## L-9. The documented language convention contradicts `CLAUDE.md`

**Location:** [docs/README.md](../README.md), [maintaining-docs.md](../maintaining-docs.md)

**Description.** Both documents state that "in the code itself, comments, log messages and
user-facing error strings stay in **Vietnamese**". `CLAUDE.md` states the opposite, and is
explicit about it:

> **Write everything in English** — code comments, log messages, user-facing error strings,
> documentation, and commit messages, as well as identifiers and API contracts.

The code follows `CLAUDE.md`: the backend has been converted to English, with the single
exception noted in [M-16](medium.md#m-16-nightly-cache-job-is-an-n1-writing-keys-nothing-reads).
`maintaining-docs.md` itself requires that `docs/` and `CLAUDE.md` never contradict each other, so
this is a defect against a stated project rule, and it will actively mislead anyone who reads the
documentation first.

**Fix.** Update the convention bullet in [docs/README.md](../README.md) and the writing principle
in [maintaining-docs.md](../maintaining-docs.md) to match `CLAUDE.md`: everything in English, with
the frontend's remaining Vietnamese UI strings described as an in-progress migration rather than
as the convention.
