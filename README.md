# Gielinor Profile Sync

Gielinor Profile Sync is a privacy-minded RuneLite plugin that writes a
versioned account snapshot to your local `.runelite` directory. It is designed
for personal dashboards and progression tools without tying the data format to
one website or brand.

## What it exports

- Skill levels and XP, including Sailing
- Quest states
- Achievement Diary tier completion
- Bank, inventory, and equipment snapshots with freshness metadata
- Grand Exchange offers and RuneLite price estimates
- A player appearance descriptor and compact local-player model for private avatars
- Manual and optional hourly character-history scenes with camera, animation,
  framing, recent-skill and coarse-region context

The recurring account profile deliberately does **not** export world number,
coordinates, FPS, animation state, account credentials, Discord details, or
other client telemetry. A history scene records camera and animation context for
that image. v0.3 can also record a recent XP-backed skill name and a coarse
64-by-64 map-region tag; it still omits exact coordinates, plane, world number,
chat data and nearby-player names from structured metadata.

## Where the file lives

After logging in, the plugin writes:

```text
%USERPROFILE%\.runelite\gielinor-profile-sync\latest.json
```

It also keeps one account-named JSON file in the same directory. Output stays
local; this plugin performs no network requests. A separate, user-controlled
sync tool may copy `latest.json` to a private service or repository.

Snapshots refresh on the configured interval and promptly after bank,
inventory, or equipment changes so short bank visits are not missed.

Character-history captures are written as PNG/JSON pairs under:

```text
%USERPROFILE%\.runelite\gielinor-profile-sync\captures\pending
```

The framing guide shows the central scene area that will be saved. In resizable
mode this crop excludes the standard chat box, minimap and action tabs. Captures
are blocked while the bank or a right-click menu is open and remain local.

Automatic history capture is **off by default**. After explicit opt-in it waits
about 60 minutes of logged-in play and only saves when a recent real XP change can
label the skill. It also collects up to two bank-context scenes after the bank has
closed. The pending queue defaults to 100 bundles, and diversity-aware pruning
preserves the two newest bank scenes plus the newest scene for every tagged skill.

## Development

Run the tests with:

```text
gradlew.bat test
```

Run the RuneLite development client with:

```text
gradlew.bat run
```

See [docs/schema-v1.md](docs/schema-v1.md) for the stable profile contract and
[docs/capture-schema-v1.md](docs/capture-schema-v1.md) for history bundles.
