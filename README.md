# Arffornia_Proxy

Velocity plugin for the Arffornia Minecraft network. Its only job is to **forward NuVotifier votes to the Arffornia
website API** (`POST /vote/nuvotifier`), authenticated with a website service account. Routing, online-mode auth,
modern forwarding and the NuVotifier listener itself are configured in Velocity / the `infra` repo, not here.
The website turns each vote into points and a pending in-game reward.

Architecture docs (coming soon): https://arffornia.github.io/docs/

## Requirements
- JDK 21 to run Gradle (the plugin is compiled for Java 17; the toolchain is provisioned automatically).
- Velocity 3.4 with the [NuVotifier](https://github.com/NuVotifier/NuVotifier) plugin installed.

## Quickstart
```sh
make check      # compile + tests + jar
ls build/libs/arffornia_proxy.jar
```
Copy the jar into Velocity's `plugins/` directory next to NuVotifier and start the proxy once to create the config.

## Commands
| Command | What it does |
|---|---|
| `make check` | `./gradlew build` (what CI runs) |
| `make build` | Build the jar only |
| `make test` | Unit tests |
| `make run` | Local Velocity with the plugin (`runVelocity`) |

All targets cap Gradle at 2 GB and disable the daemon; override with `GRADLE_ARGS=...`.

## Configuration
On first start the plugin writes `plugins/arffornia_proxy/config.properties`:

| Key | Meaning |
|---|---|
| `api.base-url` | Website API base URL, e.g. `https://arffornia.com/api` |
| `api.client-id` | Service-account client id (role `nuvotifier_service`) |
| `api.client-secret` | Service-account client secret |

The plugin stays disabled if a value is missing. In production the file is provided by
[Arffornia_Provisioning](https://github.com/Arffornia/Arffornia_Provisioning) (`config/proxy/...`) with values
templated from the deployment's secrets. Never commit real credentials.

## Release process
Push a tag `vX.Y.Z` on `main`. The `Build and Release` workflow builds with `-PpluginVersion=X.Y.Z` and attaches
`arffornia_proxy.jar` to a GitHub release. Provisioning (`config/proxy/modList.json`) downloads that asset.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md). Security issues: [SECURITY.md](SECURITY.md).

## License
[MIT](LICENSE), © Brian Perret and Arffornia contributors.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
