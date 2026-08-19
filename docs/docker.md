# Docker

The whole stack runs as four containers defined in [docker-compose.yml](../docker-compose.yml):
two images built from this repository (`backend`, `frontend`) and two pulled from Docker Hub
(`postgres:16-alpine`, `redis:7-alpine`).

This document covers the containerized setup itself. For running the pieces by hand without
Docker see [development.md](development.md); for the meaning of each environment variable see
[configuration.md](configuration.md).

## Topology

```
                    host
  :5173 ──► flashmind-frontend   (nginx:alpine, container port 80)
                    │
                    │  proxy_pass http://backend:8080   (docker network DNS)
                    ▼
  :8080 ──► flashmind-backend    (eclipse-temurin:25-jre-alpine, java -jar app.jar)
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
  :5432 flashmind-postgres   :6379 flashmind-redis
        (volume pgdata)
```

Compose creates the network `flashmind_project_default` and the volume
`flashmind_project_pgdata`, both prefixed with the project name, which is derived from the
directory name (`flashmind_project`).

Inside the network, services reach each other by **service name** — `postgres`, `redis`,
`backend` — on the container's own port. The published `host:container` ports are only for
access from your machine; the containers do not use them to talk to each other.

## Prerequisites

- Docker Engine with the Compose v2 plugin (`docker compose`, not the legacy `docker-compose`
  binary — both spellings work if you have Docker Desktop).
- The Docker daemon must actually be **running**. On Windows/macOS that means Docker Desktop is
  started; `docker compose` will otherwise fail with
  `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`.
- A `.env` file in the repository root (see below).
- Free host ports **5173, 8080, 5432, 6379**.

## The `.env` file

Compose reads `.env` from the repository root for variable substitution. Only two variables come
from it:

| Variable | Used by | If missing |
|----------|---------|------------|
| `ANTHROPIC_API_KEY` | `backend` | Substitutes to an empty string; the app starts but AI generation fails |
| `JWT_SECRET` | `backend` | Falls back to the dev secret hard-coded in the compose file |

Everything else the backend needs (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`,
`REDIS_PORT`) is set directly in `docker-compose.yml` and points at the internal service names.

```bash
cp .env.example .env      # then fill in ANTHROPIC_API_KEY
```

`.env` is git-ignored and must stay that way — it holds a live API key.

## Services

### postgres

`postgres:16-alpine`, database `flashmind`, credentials `postgres` / `postgres`. Data is
persisted in the named volume `pgdata` mounted at `/var/lib/postgresql/data`.

Healthcheck `pg_isready -U postgres`, every 5s, 5s timeout, 5 retries — so the container is
marked healthy roughly 5–25s after start.

### redis

`redis:7-alpine`, no persistence configured and no volume: **everything in Redis is lost when the
container is removed.** That is acceptable today because Redis is write-only — `SchedulerService`
writes `due_cards:{userId}` keys and nothing ever reads them (see [backend.md](backend.md)).

Healthcheck `redis-cli ping`, same 5s/5s/5 timing.

### backend

Built from [backend/Dockerfile](../backend/Dockerfile). Publishes `8080:8080`.

`depends_on` uses `condition: service_healthy` for both postgres and redis, so the JVM does not
start until the databases answer. This matters because Hibernate runs `ddl-auto=update` at
startup and would fail against a database that is not accepting connections yet.

The container itself declares **no healthcheck**, so `docker compose ps` shows it as `Up` the
instant the process starts, several seconds before Spring finishes booting. To know it is really
ready, poll the actuator endpoint:

```bash
curl -s http://localhost:8080/actuator/health     # {"status":"UP"}
```

On a warm machine this returns `UP` about 5–10 seconds after the container starts.

### frontend

Built from [frontend/Dockerfile](../frontend/Dockerfile). Publishes `5173:80` — note the port
translation: nginx listens on 80 inside the container, and 5173 was chosen on the host to match
the port the Vite dev server uses, so the URL is the same either way.

`depends_on: backend` here is the plain list form with **no** `condition`, so it only orders
startup; nginx comes up while the backend is still booting. This is harmless: nginx resolves and
connects to `backend:8080` per request, not at startup, so early requests get a 502 and
everything works once the backend is up.

## How the images are built

Both Dockerfiles are two-stage: a fat build stage, then a small runtime stage that receives only
the artifact.

**Backend** — `maven:3.9-eclipse-temurin-25` → `eclipse-temurin:25-jre-alpine`, final image
~499MB.

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline -B      # cached layer: only re-runs when pom.xml changes
COPY src ./src
RUN mvn clean package -DskipTests
```

Copying `pom.xml` before `src` is deliberate: the dependency download is its own layer, so
editing Java source re-runs only the `package` step instead of re-downloading the world.

**Tests are skipped in the image build** (`-DskipTests`). A green `docker compose build` says
nothing about whether the test suite passes — run `mvn test` separately.

**Frontend** — `node:20-alpine` → `nginx:alpine`, final image ~93MB. Builds with `npm run build`,
which runs `tsc -b` first, so **a TypeScript error fails the image build**. The `dist` output is
copied to `/usr/share/nginx/html` and [nginx.conf](../frontend/nginx.conf) is installed as the
default site: `try_files $uri $uri/ /index.html` for SPA routing, plus the `/api/` reverse proxy.

Note it runs `npm install`, not `npm ci`, so the build does not strictly honour
`package-lock.json`.

### No `.dockerignore`

There is no `.dockerignore` at the repository root, in `backend/`, or in `frontend/`. Every build
therefore ships the full directory as build context — including `backend/target/`,
`frontend/node_modules/`, `frontend/dist/` and `.git/` if they exist locally. Consequences:

- Builds are slower than they need to be, and the context grows over time.
- `frontend/Dockerfile` does `COPY . .` after `npm install`, so a **host** `node_modules` built
  on a different platform overwrites the one installed inside the image. If a frontend build
  fails in Docker but works locally, this is the first thing to check.

Adding `.dockerignore` files is a genuine improvement, but it is not done today.

## Commands

```bash
docker compose up -d                     # build if needed, then start everything
docker compose up -d --build             # force a rebuild first
docker compose build                     # build the images without starting
docker compose ps                        # status and published ports
docker compose logs -f backend           # follow one service
docker compose logs --tail=100           # recent output from all services
docker compose restart backend
docker compose down                      # stop and remove containers + network, keep the volume
docker compose down -v                   # ALSO delete pgdata — every deck, card and user is gone
docker compose up postgres redis -d      # infrastructure only, for local backend/frontend dev
```

**Code changes do not appear until you rebuild.** Nothing is bind-mounted; the source is baked
into the image at build time. After editing Java or TypeScript, use `docker compose up -d --build`
(or run the app outside Docker for a fast edit loop, per [development.md](development.md)).

Open a shell or a psql session inside a running container:

```bash
docker compose exec backend sh
docker compose exec postgres psql -U postgres -d flashmind
docker compose exec redis redis-cli keys 'due_cards:*'
```

## Data and persistence

| What | Where | Survives `down` | Survives `down -v` |
|------|-------|-----------------|--------------------|
| PostgreSQL data | volume `flashmind_project_pgdata` | yes | **no** |
| Redis data | container filesystem only | **no** | no |
| Uploaded files | nowhere — parsed in memory, never written to disk | n/a | n/a |

The schema is created by Hibernate `ddl-auto=update` on backend startup; there are no migrations,
so an empty `pgdata` volume is populated automatically the first time the backend boots.

Back up the database with a plain `pg_dump` through the container:

```bash
docker compose exec -T postgres pg_dump -U postgres flashmind > backup.sql
```

## Troubleshooting

**`failed to connect to the docker API` / `Cannot connect to the Docker daemon`**
The daemon is not running. Start Docker Desktop (or `systemctl start docker`) and wait until
`docker info` succeeds before retrying.

**`Bind for 0.0.0.0:5432 failed: port is already allocated`**
Something on the host already owns the port — most often a natively installed PostgreSQL. It is a
Windows service (`postgresql-x64-NN`) or a systemd unit (`postgresql`), and it starts
automatically at boot, so this recurs after every reboot. Identify and stop it:

```bash
# Windows (PowerShell, elevated)
Get-NetTCPConnection -LocalPort 5432 -State Listen
Stop-Service postgresql-x64-18          # Start-Service to put it back

# Linux/macOS
sudo lsof -i :5432
sudo systemctl stop postgresql
```

The same applies to 6379 (a local Redis), 8080 and 5173.

**`the attribute 'version' is obsolete, it will be ignored`**
`docker-compose.yml` still starts with `version: '3.8'`. Compose v2 ignores the key entirely and
warns about it. Harmless; removing the line silences it.

**The backend container starts, then exits**
Read `docker compose logs backend`. The usual causes are a `JWT_SECRET` that is not valid Base64
or is shorter than 32 bytes once decoded, and a database that rejected the connection.

**AI generation returns a 400/500 while everything else works**
`ANTHROPIC_API_KEY` is missing or wrong in `.env`. Compose substitutes an unset variable with an
empty string without complaining, so the container starts perfectly and only the generation
endpoint fails. Verify what the container actually received:

```bash
docker compose exec backend printenv ANTHROPIC_API_KEY
```

Changing `.env` requires `docker compose up -d` again — environment variables are fixed when the
container is created.

**The frontend loads but every API call 502s**
The backend is not up yet, or it crashed. Check `docker compose ps` and the backend logs.

**A rebuild does not pick up changes**
Docker reused a cached layer. Force a clean build with
`docker compose build --no-cache backend`.

## Known limitations

These are properties of the setup as it stands today, not a plan:

- **No `restart:` policy** on any service, so nothing comes back automatically after a reboot or
  a daemon restart. Run `docker compose up -d` again.
- **No `.dockerignore`** anywhere (see above).
- **No healthcheck** on the backend or frontend containers; only postgres and redis have one.
- **No resource limits** (`mem_limit`, `cpus`) — the Maven build stage in particular is happy to
  use everything available.
- **Development credentials are committed**: `postgres/postgres` in the compose file and a
  fallback `JWT_SECRET`. See the production checklist in [configuration.md](configuration.md).
- **Ports are published on `0.0.0.0`**, so postgres and redis are reachable from the local
  network, not just from the host.
- The compose file describes a **single-machine development setup**. There is no production
  overlay, no TLS termination and no image registry in the repository.
