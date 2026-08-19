# Development

## Requirements

- Docker + Docker Compose (the recommended way to run everything)
- Or, running by hand: JDK 25, Maven (**there is no Maven wrapper** — use the system `mvn`),
  Node.js 20+, PostgreSQL 16, Redis 7
- An Anthropic API key (only needed for AI card generation)

## Running everything with Docker

```bash
cp .env.example .env          # then fill in ANTHROPIC_API_KEY
docker-compose up -d
docker-compose logs -f backend
```

| Service | Port |
|---------|------|
| Frontend (nginx) | http://localhost:5173 |
| Backend | http://localhost:8080 |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |

The backend waits for postgres and redis to be healthy before starting. Postgres data lives in
the `pgdata` volume, so `docker-compose down` does not lose data (`down -v` does).

To bring up only the infrastructure for local development:

```bash
docker-compose up postgres redis -d
```

## Backend (`backend/`)

```bash
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run

mvn clean package -DskipTests
```

### Tests

```bash
mvn test                                                       # everything
mvn test -Dtest=ReviewServiceTest                              # one class
mvn test -Dtest=ReviewServiceTest#firstReviewWithGoodQuality   # one method
```

There are currently 14 Mockito unit tests in `backend/src/test/java/com/flashmind/service/`
(`ReviewServiceTest` 9, `DeckServiceTest` 3, `FlashcardServiceTest` 2). They run standalone and
**need neither a database nor Redis**. There are no integration tests and no controller tests.

## Frontend (`frontend/`)

```bash
npm install
npm run dev        # http://localhost:5173, proxies /api → localhost:8080
npm run build      # tsc -b && vite build
npm run preview    # preview the production build
```

### Notes

- `npm run build` runs `tsc -b` first, and `tsconfig.json` enables `noUnusedLocals` +
  `noUnusedParameters` — **a single unused import is enough to break the build**.
- To type-check without building: `npx tsc -b --noEmit`.
- `npm run lint` **does not work**: the repo has no eslint installed and no eslint config file.
  Use the type check above, or add eslint if linting is actually needed.
- There are no frontend tests and no test infrastructure is configured.

## A quick try with curl

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"123456","fullName":"Test User"}'

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"123456"}'
```

Take `accessToken` from the response and carry on:

```bash
curl http://localhost:8080/api/decks -H "Authorization: Bearer <accessToken>"
```

## Demo flow

1. Register at `/register`
2. Create a new deck
3. Open the deck → upload a PDF/TXT → the AI generates the cards
4. Go to **Review** → flip a card → grade it 0–5
5. Go to **Analytics** to see your streak and progress

## Coding conventions

- In the code, comments, log messages and user-facing error strings are written in
  **Vietnamese**; identifiers and API contracts in **English**. Documentation is in English.
- Controllers hold no business logic; services take `userId` as a parameter and never read the
  security context.
- A new endpoint must check permissions with `findDeckOwnedBy` / `findCardOwnedBy`
  (see [backend.md](backend.md)).
- Every delete path must clean up `card_reviews` itself (see [data-model.md](data-model.md)).
- When code changes, update the matching documentation (see [maintaining-docs.md](maintaining-docs.md)).
