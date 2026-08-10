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
- A player appearance descriptor: body colours, kits, and equipped item IDs

It deliberately does **not** export world number, coordinates, FPS, animation
state, account credentials, Discord details, or other client telemetry.

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

## Development

Run the tests with:

```text
gradlew.bat test
```

Run the RuneLite development client with:

```text
gradlew.bat run
```

See [docs/schema-v1.md](docs/schema-v1.md) for the stable top-level contract.
