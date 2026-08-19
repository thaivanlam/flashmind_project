# Configuration

All backend configuration lives in
[application.properties](../backend/src/main/resources/application.properties);
every value can be overridden with an environment variable.

## Backend environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/flashmind` | PostgreSQL connection string |
| `DB_USER` | `postgres` | DB account |
| `DB_PASSWORD` | `postgres` | DB password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | an embedded dev secret | JWT signing key, **Base64-encoded** |
| `ANTHROPIC_API_KEY` | `your-api-key-here` | **Required** for AI card generation |

## Fixed properties

| Property | Value | Meaning |
|----------|-------|---------|
| `server.port` | `8080` | |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema generated from the entities, no migrations |
| `app.jwt.access-expiration` | `3600000` | Access token lives 1 hour |
| `app.jwt.refresh-expiration` | `604800000` | Refresh token lives 7 days |
| `anthropic.model` | `claude-opus-5` | Model used for card generation |
| `anthropic.max-tokens` | `16000` | Response cap, required by the Claude API |
| `anthropic.timeout-seconds` | `120` | Overrides the SDK's 10-minute default |
| `spring.servlet.multipart.max-file-size` | `5MB` | Upload size limit |
| `spring.servlet.multipart.max-request-size` | `5MB` | |
| `app.cors.allowed-origins` | `http://localhost:5173,http://localhost:3000` | Comma-separated list of origins |
| `logging.level.com.flashmind` | `DEBUG` | |

## Frontend environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_URL` | `/api` | Base URL for axios. Leave empty to go through the proxy (Vite in dev, nginx in production) |

## The `.env` file for Docker Compose

`docker-compose.yml` reads `.env` from the repository root. Create it from the template:

```bash
cp .env.example .env
```

```
ANTHROPIC_API_KEY=sk-ant-your-anthropic-api-key-here
JWT_SECRET=<a Base64 string>
```

Compose sets `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST` and `REDIS_PORT` itself, pointing
at the internal services; only the two variables above are yours to provide. `JWT_SECRET` has a
fallback value embedded in the compose file if you do not set it.

## About `JWT_SECRET`

`JwtUtil` **Base64-decodes** this value before using it as the HMAC key. The secret must
therefore be a valid Base64 string, and long enough for HS256 once decoded (at least 32 bytes).

Generate a new secret:

```bash
openssl rand -base64 48
```

## Mandatory checks before going to production

1. **Change `JWT_SECRET`.** The default value is committed in the repository — public to anyone
   who can read the source.
2. **Change the PostgreSQL password.** `postgres/postgres` is for development only.
3. **Reset `app.cors.allowed-origins`** to the real domain; the default is localhost only.
4. **Lower the log level** of `logging.level.com.flashmind` from `DEBUG` to `INFO`.
5. Consider restricting `/actuator/**` — it is currently public.
6. Remember there is **no token revocation**: a leaked refresh token stays usable for its full
   7 days.

## Proxy configuration

| Environment | Mechanism |
|-------------|-----------|
| Dev | `vite.config.ts`: `/api` → `http://localhost:8080` |
| Production | [nginx.conf](../frontend/nginx.conf): `location /api/` → `http://backend:8080`, everything else `try_files … /index.html` for SPA routing |

## Docker

Details of the container stack — topology, build layers, volumes and troubleshooting — are in
[docker.md](docker.md).

- [backend/Dockerfile](../backend/Dockerfile): multi-stage build, `maven:3.9-eclipse-temurin-25`
  → `eclipse-temurin:25-jre-alpine`. Tests are skipped (`-DskipTests`) in the image build step.
- [frontend/Dockerfile](../frontend/Dockerfile): `node:20-alpine` builds, `nginx:alpine` serves
  the `dist` folder.
