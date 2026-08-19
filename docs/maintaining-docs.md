# Maintaining the documentation

The documentation in `docs/` is part of the source: a code change and its documentation update
travel together in one change, with no "will update later" left behind.

## Process

**Before starting work:** read [README.md](README.md), then open the documents covering the area
you are about to touch.

**After changing code:** look it up in the table below and update every affected document.

## Code → documentation map

| What you changed | Documents to update |
|------------------|---------------------|
| Adding/changing/removing an endpoint (controller, DTO, validation) | [api-reference.md](api-reference.md), [backend.md](backend.md); if the client changes too, [frontend.md](frontend.md) |
| Entities, columns, constraints, data deletion paths | [data-model.md](data-model.md) |
| `ReviewService.applySpacedRepetition`, `MASTERY_THRESHOLD` | [spaced-repetition.md](spaced-repetition.md) **and** `ReviewServiceTest` |
| A new service, or a service changing responsibility | [backend.md](backend.md) |
| Security: JWT, filter chain, authorization rules | [backend.md](backend.md), [architecture.md](architecture.md); if configuration is affected, [configuration.md](configuration.md) |
| Cron jobs, how Redis is used | [backend.md](backend.md), [architecture.md](architecture.md) |
| The AI prompt, file parsing, character/size limits | [backend.md](backend.md), [api-reference.md](api-reference.md) |
| Error codes, `GlobalExceptionHandler` | [api-reference.md](api-reference.md), [backend.md](backend.md) |
| Routes, Redux slices, the axios layer, token storage | [frontend.md](frontend.md) |
| TypeScript interfaces mirroring DTOs | [frontend.md](frontend.md), [api-reference.md](api-reference.md) |
| `application.properties`, environment variables, `.env.example` | [configuration.md](configuration.md) |
| `docker-compose.yml`, the Dockerfiles, `nginx.conf` | [configuration.md](configuration.md), [development.md](development.md) |
| Build/test scripts, adding or removing tests | [development.md](development.md); the test count also appears in [backend.md](backend.md) |
| Architecture or a cross-cutting design decision | [architecture.md](architecture.md), and `CLAUDE.md` in the repository root as well |
| Fixing anything listed in [audit/](audit/README.md) | Move the finding to the "Fixed" table in its severity file, with the fixing commit, and update the totals in [audit/README.md](audit/README.md) |

## When adding a new document

1. Put the file in `docs/`, kebab-case name, `.md` extension.
2. Add a row to the index table in [README.md](README.md).
3. If it describes an area of the code, add the matching row to the map above.

## Writing principles

- **Describe the code as it is, not as it ought to be.** Unfinished parts (Redis write-only, no
  token revocation, no migrations) must be stated as unfinished.
- Give concrete numbers: crons, timeouts, thresholds, limits — that is what readers come for.
- Link to source files with relative paths so the reader can jump straight into the code.
- Do not paste long blocks of code; quote only the part that shows the rule.
- Documentation prose is in English. In the code itself, comments, log messages and user-facing
  error strings stay in Vietnamese — quote those strings verbatim when documenting them.

## Relationship with `CLAUDE.md` and `README.md`

| File | Role |
|------|------|
| `docs/**` | All the detailed documentation of the project |
| `README.md` (root) | A short landing page for GitHub, pointing at `docs/` |
| `CLAUDE.md` (root) | Guidance specific to Claude Code, summarizing the architectural traps |

When an architectural rule changes, both `docs/` and `CLAUDE.md` must reflect it — these two must
never contradict each other.
