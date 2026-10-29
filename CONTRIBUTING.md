# Contributing

## Setup
JDK 21 and `make`. Clone, then run `make check`.

## Workflow
1. Branch from `main`: `fix/<slug>`, `security/<slug>`, `ci/<slug>`, `docs/<slug>`, `chore/<slug>`.
2. Commit with [Conventional Commits](https://www.conventionalcommits.org/) in English (`fix(proxy): ...`).
3. Run `make check` before pushing.
4. Open a PR against `main` using the template (What / Why / How tested / Risk & revert) and link the issue
   (`Closes #n`). The maintainer reviews and merges.

Never commit credentials: `config.properties` in the repo must hold placeholders only.

More context for agents and newcomers: [AGENTS.md](AGENTS.md).
