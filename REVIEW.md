# REVIEW.md — Arffornia_Proxy

How to review a change in this repo, for AI reviewers (`/arffornia:review`, the
`arffornia:reviewer` agent) and humans. The plugin is small: one listener, one HTTP client.
Most of the risk is in how it talks to the website API and what it puts in config and logs.

## Output
- First line: `Findings: N (blocking b, major m, minor k) · risk: high|medium|low`.
- Every finding cites `path:line` and the evidence (quoted code, or a command and its output).
  No evidence: ask a question instead.
- At most 3 style remarks.
- End with a verdict: approve, request changes, or comment. Say what you did not verify.

## Depth by risk
| Risk | Paths in this repo | Depth |
|---|---|---|
| High | `service/ApiService.java` (token, auth, HTTP), `src/main/resources/config.properties`, `.github/workflows/release.yml`, `build.gradle` dependencies and repositories | Every line, trace callers, try to break it |
| Medium | `listener/VoteListener.java`, `config/PluginConfig.java`, `Arffornia_Proxy.java` (wiring, threads) | Every line, check tests cover it |
| Low | docs, `AGENTS.md`, tests only, `.github/` templates | Skim |

## Always check
- `config.properties` holds placeholders only; no token, URL with credentials or key is committed.
- No token or full API response body in logs (log status codes and ids, not secrets).
- Every HTTP call has a connect and request timeout; retries are bounded with backoff; a 401
  triggers at most one shared re-auth (each re-auth mints a new website token).
- Vote handling never blocks the Velocity event thread (async send, no `.join()` on it).
- Failures are logged loudly with the player and service, never swallowed silently.
- New dependencies are `compileOnly` when Velocity or NuVotifier already provides them at runtime,
  and come from a pinned version (not `-SNAPSHOT`, see #5).
- `make check` output is in the PR; `ApiService` changes come with a test against the
  `HttpServer` stub.

## Skip
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` (unless the wrapper version changes).
- `build/`, `run/`.
