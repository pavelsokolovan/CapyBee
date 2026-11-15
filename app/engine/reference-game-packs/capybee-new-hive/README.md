# CapyBee — Nowy ul, dwa światła

A short authored Polish adventure, version **1.0.0**. Seven places on a fictional
Honeycomb Board, three optional animal companions and three equally valid
resolutions. Remembering home, exploring a new place and resting all have room.

All story text is Polish; the application shell remains EN/PL and labels the
story language explicitly. There are no external images/links, generated asset
URLs, timed choices, combat, affection scores or free-text inputs. No provider
or Spring service is needed to play or resume. Generation is not advertised.

See [CONTENT-INVENTORY.md](CONTENT-INVENTORY.md) for the exact scene/place/actor
IDs, mechanics, optional-step proposals, editorial review and resource digest.
The universal runtime/console schemas are unchanged. The existing English Pack
and its saved formats/resources are untouched.

From the repository root, after building the engine CLI:

```sh
GENERATION_PROVIDER_MODE=disabled java -jar app/engine/cli/target/engine-cli-0.1.0-SNAPSHOT.jar \
  play --pack-dir app/engine/reference-game-packs/capybee-new-hive --offline \
  --memory --script app/engine/certification/authored-reference-v1/routes/new-path-without-companions.script
```

For interactive durable play, replace `--memory --script …` with
`--store-dir /path/to/local-saves`. `/quit` pauses; run the same command with
`--continue` to resume in a new provider-disabled process. Scripted mode is strict:
it must reach an ending or explicitly `/quit`; invalid/stuck routes cannot pass.

Server packaging copies the exact indexed resources to
`game-packs/capybee-new-hive`. An operator may add `capybee-new-hive` to the
existing `capybee.game.pack-ids` classpath catalog allowlist in a development
configuration. All production/API/client/generation flags retain their defaults.
No Mission/reward endpoint or production activation is introduced here.

The CLI distribution also carries this Pack and the strict authored route
scripts. This authored edition does not accept `--generated`; optional theme-first
generation and its privacy boundary belong to 29.2, with a separately versioned
content change and release acceptance under 29.5.
