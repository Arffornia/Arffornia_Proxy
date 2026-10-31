# Server-list MOTD

The MOTD (the two lines under the server name in Multiplayer) has **one source of truth**:

    Arffornia_Provisioning: config/proxy/storage/plugins/arffornia_proxy/motd.yml

The provisioning init container writes that file into the proxy's plugin data directory
(`plugins/arffornia_proxy/motd.yml`, `override: true`), and this plugin reads it. Nothing else
defines the MOTD:

- the jar ships **no** copy of `motd.yml`, and never writes one;
- Velocity's own `motd` in `velocity.toml` is never shown, because the plugin always sets the
  description. Provisioning keeps it as a neutral placeholder only because Velocity requires
  the key;
- if the file is missing or invalid, the server list shows the bare word "Arffornia" (aqua) and
  the proxy logs where the file should come from. An invalid edit keeps the last valid version.

## How to edit it

1. Open a PR on Arffornia_Provisioning that changes `motd.yml` (the file header documents the
   format: MiniMessage text, `en` and `fr` per entry, `dates`, `priority`, `weight`), and run
   `npm run generateIndexes` so `config/proxy/index.json` gets the new hash.
2. After merge, the proxy picks it up at its next start. A running proxy also reloads the file
   when it changes on disk.
3. Preview any day and language: `/motdpreview 2027-07-26 fr` (permission
   `arffornia.motd.preview`).

## Placeholders

Besides standard MiniMessage, a line may use `<age:YYYY>`: the number of years from `YYYY` to
the displayed day. The anniversary entry uses `<age:2016>` on 26 July (Arffornia was founded on
26 July 2016), so it reads "Arffornia turns 10 today!" in 2026.
