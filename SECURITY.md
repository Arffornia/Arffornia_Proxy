# Security policy

## Supported versions
Only the latest release built from `main` is supported.

## Reporting a vulnerability
Please **do not open a public issue**. Use GitHub's private reporting instead:
[Report a vulnerability](https://github.com/Arffornia/Arffornia_Proxy/security/advisories/new)
(repository "Security" tab → "Report a vulnerability").

Include what is affected, how to reproduce it, and the impact you expect. You will get an answer as soon as
the maintainer can look at it.

## Known leaked credential in history

The proxy service-account secret was committed in `src/main/resources/config.properties` (commit `7d56fffd5e`). It was rotated on 2026-10-31 and the old value is rejected with 401. The history is intentionally not rewritten, because that would break open pull requests. No action is needed.
