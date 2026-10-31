# AGENTS.md — Arffornia_Proxy

Guide for AI agents and newcomers. Read this before changing anything.

## Purpose
Velocity plugin that forwards NuVotifier `VotifierEvent`s to the Arffornia website API (`POST /vote/nuvotifier`)
using a service-account Sanctum token (`POST /auth/token/svc`). Nothing else (no routing, no auth, no whitelist).

## Repo map
| Path | What |
|---|---|
| `src/main/java/.../Arffornia_Proxy.java` | `@Plugin` entry point; loads config, wires the listener |
| `src/main/java/.../config/PluginConfig.java` | Reads `plugins/arffornia_proxy/config.properties` (copied from the jar default on first run) |
| `src/main/java/.../listener/VoteListener.java` | `@Subscribe VotifierEvent` → `ApiService.sendVote` |
| `src/main/java/.../service/ApiService.java` | `java.net.http` client, token cache, vote POST |
| `src/main/resources/config.properties` | Default config shipped in the jar (must contain placeholders only, see #2) |
| `src/main/templates/` | `BuildConstants.VERSION`, expanded at build time |
| `.github/workflows/release.yml` | Tag `v*` → GitHub release with the jar |
| `.github/workflows/ci.yml` | PR / push build (`make check`) |

## Commands
- `make check` — full build (CI equivalent). `make test`, `make build`, `make run` (local Velocity).
- Gradle is capped: `--no-daemon -Dorg.gradle.jvmargs=-Xmx2g`. Never run two Gradle builds at once.
- Maven Central sometimes answers 429 on shared egress: retry once after a pause.

## Conventions
- English everywhere (code, comments, logs, docs, commits, PRs).
- Conventional Commits (`fix(proxy): ...`), branches `fix/…`, `security/…`, `ci/…`, `docs/…`, `chore/…`.
- One PR per issue, `Closes #n`, body: What / Why / How it was tested / Risk & revert.

## Hard rules
- **No secrets in the repo.** `config.properties` must hold placeholders only; real values come from provisioning.
- Never merge, tag, release, or push to `main`; open PRs. Do not touch release workflow secret usage.
- Do not replace the owner's libraries (Nexus_*) with third-party ones.
- Design choices (`needs-decision` issues) belong to the maintainer.
- Security weaknesses not yet fixed: neutral issue/PR wording, no exploit details.

## Testing strategy
- Unit tests (JUnit 5) for `ApiService` against a local `com.sun.net.httpserver.HttpServer` stub:
  token fetch, 401 → exactly one re-auth, 5xx, timeouts.
- Manual smoke test: `make run`, install NuVotifier in `run/plugins`, send a test vote.

## Gotchas (from the audit)
- Builds against `velocity-api:3.4.0-SNAPSHOT` (not reproducible) — see #5.
- NuVotifier comes from jitpack and must be declared as a plugin dependency — see #4.
- Votes are dropped when the API is down (no queue) — see #8.
- The website token never expires; each re-auth mints a new token, so keep re-auth bounded and shared.

## Related repos
- `Arffornia_Website` — Laravel API (`routes/api.php`, `VoteService`).
- `Arffornia_Provisioning` — ships `config/proxy/storage/plugins/arffornia_proxy/config.properties`.
- `infra` — Velocity/NuVotifier deployment (`k8s/config/arffornia/proxy/values.yaml`).
- `Arffornia_Mods` — NeoForge mod (claims vote rewards in game).
