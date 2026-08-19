# Critical findings

Three findings. Each is either a complete authentication bypass, a total loss of session
continuity for every user, or a whole-application outage reachable by ten ordinary requests.

See the [audit index](README.md) for scope, method and remediation order.

---

## C-1. Publicly known JWT signing key is the silent production default

| | |
|---|---|
| **Severity** | Critical |
| **Location** | [application.properties:17](../../backend/src/main/resources/application.properties), [docker-compose.yml:44](../../docker-compose.yml), [.env.example:3](../../.env.example), [JwtUtil.getSigningKey](../../backend/src/main/java/com/flashmind/security/JwtUtil.java) |

### Description

The HS256 signing secret defaults to a literal committed to the repository:

```properties
app.jwt.secret=${JWT_SECRET:VGhpc0lzQVZlcnlMb25nU2VjcmV0S2V5Rm9yRmxhc2hNaW5kQXBwbGljYXRpb24yMDI2}
```

`docker-compose.yml` repeats the same value as its own fallback
(`JWT_SECRET: ${JWT_SECRET:-VGhpc0lz…}`), and `.env.example` ships it as the suggested value —
so an operator who copies the example file receives the compromised key while believing they set
one.

Anyone with access to the repository can decode the secret and forge a token containing
`{"userId": N, "type": "access"}` for any `N`. `JwtAuthenticationFilter` trusts the `userId`
claim with no database lookup and no session state, so a forged token is indistinguishable from
a real login. That grants read, write and delete access to every user's decks, cards and review
history — a complete authentication bypass.

`JwtUtil` also performs no validation of the configured secret. A value that is not valid
Base64, or that decodes to fewer than the 32 bytes HS256 requires, fails at the first request
rather than at startup.

### Trigger

Any deployment where `JWT_SECRET` is unset, or is copied from `.env.example`. There is no
startup check and no warning log — the application boots and appears healthy.

### Fix

Remove every default so a missing secret is a startup failure, and validate key strength once:

```java
// JwtUtil — key built once, secret validated at startup
@PostConstruct
void initSigningKey() {
    byte[] decoded;
    try {
        decoded = Base64.getDecoder().decode(secret);
    } catch (IllegalArgumentException e) {
        throw new IllegalStateException("app.jwt.secret must be Base64-encoded", e);
    }
    if (decoded.length < 32) {
        throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes");
    }
    this.signingKey = Keys.hmacShaKeyFor(decoded);
}
```

```properties
app.jwt.secret=${JWT_SECRET}
```

```yaml
JWT_SECRET: ${JWT_SECRET:?JWT_SECRET must be set}
```

Replace the value in `.env.example` with a placeholder such as
`JWT_SECRET=generate-with-openssl-rand-base64-32`.

Caching the key in `@PostConstruct` also resolves [L-4](low.md#l-4-every-request-parses-the-jwt-four-times).

**Rotation is part of the fix.** Every token ever issued by a deployment that used the committed
key must be treated as compromised.

---

## C-2. Expired access tokens return 403, so the SPA never refreshes

| | |
|---|---|
| **Severity** | Critical |
| **Location** | [SecurityConfig.filterChain](../../backend/src/main/java/com/flashmind/config/SecurityConfig.java), [axiosClient.ts:35-40](../../frontend/src/api/axiosClient.ts) |

### Description

`SecurityConfig` registers no authentication mechanism — no `httpBasic`, no `formLogin`, and no
explicit `exceptionHandling`. In Spring Security 6, when no configurer supplies an entry point,
`ExceptionHandlingConfigurer` falls back to `Http403ForbiddenEntryPoint`. Every unauthenticated
request is therefore rejected with **403 Forbidden**, not 401.

That includes requests carrying an **expired** JWT: `JwtAuthenticationFilter` catches the parse
failure, logs it, and lets the chain continue unauthenticated, which lands on the same 403.

The frontend refresh interceptor keys strictly on 401:

```ts
if (error.response?.status === 401 && originalRequest && !originalRequest._retry && …)
```

A 403 falls straight through to `Promise.reject`. The refresh never fires and the redirect to
`/login` never fires. Meanwhile `ProtectedRoute` keeps rendering, because `isAuthenticated` is
derived from the cached user object in `localStorage` rather than from the token (see
[L-2](low.md#l-2-isauthenticated-is-derived-from-the-cached-user)). The user is left on a
logged-in-looking application where every request fails, with no way out but clearing storage by
hand.

The same status also collides with the ownership checks, which legitimately return 403. The
client cannot distinguish "this deck is not yours" from "your session expired".

### Trigger

Wait one hour (`app.jwt.access-expiration=3600000`) with the tab open, then click anything.
Fully reproducible, and it affects every user of the application.

### Fix

Return 401 for authentication failures and reserve 403 for authorization failures:

```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint((req, res, e) ->
        res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
    .accessDeniedHandler((req, res, e) ->
        res.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")))
```

This is also a prerequisite for [L-1](low.md#l-1-concurrent-401s-each-fire-their-own-refresh):
the concurrent-refresh race only becomes reachable once the refresh branch can actually run.

---

## C-3. The Claude call runs inside a database transaction

| | |
|---|---|
| **Severity** | Critical |
| **Location** | [AiGenerationService.generateFromFile](../../backend/src/main/java/com/flashmind/service/AiGenerationService.java) |

### Description

```java
@Transactional
public List<FlashcardResponse> generateFromFile(Long deckId, Long userId, MultipartFile file, int count) {
    deckService.findDeckOwnedBy(deckId, userId);   // first SELECT → JDBC connection acquired
    String content = fileParsingService.extractText(file);   // PDFBox parse
    GeneratedCards generated = callClaude(content, count);   // blocking HTTP, up to 120s
    …
}
```

Hibernate acquires the JDBC connection on the first statement and holds it until the transaction
commits. The ownership `SELECT` opens it, and the connection then stays pinned across PDF parsing
*and* the entire blocking Claude round-trip — configured at `anthropic.timeout-seconds=120`.

HikariCP's default `maximum-pool-size` is **10**. Ten concurrent generations exhaust the pool,
and every other endpoint in the application — login, deck list, review submission — begins
throwing `SQLTransientConnectionException` after the 30-second connection timeout. A feature used
by one user takes down the service for all of them.

It is worse than 120 seconds. The Anthropic Java SDK retries twice by default, so a request that
times out holds its connection for roughly 360 seconds of wall clock — and bills two additional
generations while doing so.

### Trigger

Ten users pressing the generate button within the same two-minute window. Trivially reached by
one user with a script, and reachable organically at around ten active users.

### Fix

Perform the network call outside any transaction, and open a short transaction only for the
writes:

```java
// no @Transactional on this method
public List<FlashcardResponse> generateFromFile(Long deckId, Long userId,
                                                MultipartFile file, int count) {
    deckService.findDeckOwnedBy(deckId, userId);
    String content = fileParsingService.extractText(file);
    GeneratedCards generated = callClaude(content, count);   // outside the transaction
    return persistGenerated(deckId, userId, generated);      // @Transactional, milliseconds
}
```

`persistGenerated` must live in a separate bean (or be reached through an injected self-proxy)
for the `@Transactional` proxy to apply, and must re-check ownership — the deck can be deleted
during a two-minute call.

Two supporting changes: cap in-flight generations with a `Semaphore` so concurrency can never
exceed a configured limit, and set the SDK's `maxRetries` explicitly rather than inheriting the
default of 2.
