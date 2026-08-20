# Gielinor Profile Sync

Gielinor Profile Sync is a privacy-minded RuneLite plugin that writes a
versioned account snapshot to your local `.runelite` directory. It is designed
for personal dashboards and progression tools without tying the data format to
one website or brand.

## What it exports

- Skill levels and XP, including Sailing
- Quest states
- Achievement Diary task counts, tier completion, and separately claimed rewards
- Individual Collection Log slot state for pages the player has opened; unvisited pages remain explicitly unknown
- Bank, inventory, and equipment snapshots with freshness metadata
- Owned Sailing boat slots, raw component state, hull condition, and freshness-aware cargo holds
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

Collection Log progress is deliberately observation-based. Open the Collection
Log and visit a page to teach the exporter that page's exact obtained and
missing slots. The exporter keeps those page observations between sessions,
but never treats a banked item as proof of a Collection Log unlock and never
labels an unvisited page as empty. Player-owned-house Collection Log views are
ignored so another player's state cannot enter the export.

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

### Windows developer-preview bootstrap

The releaseable pre-Plugin-Hub Windows bootstrap contains only the thin
Gielinor Profile Sync application JAR, its BSD license, bootstrap notices and a
download manifest. The installer obtains each exact RuneLite/runtime dependency
from RuneLite's official Maven repository or Maven Central in the user's
context and must verify its pinned size and SHA-256 before launch. No JUnit,
Hamcrest, test output, RuneLite JAR or native library is redistributed in this
ZIP. Build it deterministically with Java 11:

```text
gradlew.bat clean test previewBootstrapZip
```

The artifact is written to:

```text
build\distributions\gielinor-profile-sync-preview-bootstrap-0.3.3.zip
```

Verify its exact four-file boundary, manifest, hashes, download origins and
thin-JAR class boundary with Windows PowerShell 5.1:

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-PreviewBootstrap.ps1 -Path .\build\distributions\gielinor-profile-sync-preview-bootstrap-0.3.3.zip
```

The preview starts a separate RuneLite profile named
`gielinor-profile-sync-preview`; it does not install code into normal RuneLite.
The complete `previewDistZip` with third-party JARs remains available strictly
for private/local validation. Do not publish or embed that full ZIP: the
redistribution rights for RuneLite's injected client and rlicn native payload
are not established by their artifact metadata.

See [docs/schema-v1.md](docs/schema-v1.md) for the stable profile contract and
[docs/capture-schema-v1.md](docs/capture-schema-v1.md) for history bundles.
