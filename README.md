# Gielinor Profile Sync

Gielinor Profile Sync is a privacy-minded RuneLite plugin that writes a
versioned account snapshot to your local `.runelite` directory. It is designed
for personal dashboards and progression tools without tying the data format to
one website or brand.

## What it exports

- Skill levels and XP, including Sailing
- A small selection of distinct character poses for compatible avatar displays
- A sidebar checklist for a Construction or Cooking plan saved by your companion app
- Manual and opt-in automatic character-history scenes, including portraits of your own Sailing boats
- Sailing boat names, fitted components, and observed cargo with freshness information
- House furniture and room layouts observed while visiting your own house in building mode
- Hunter Rumours observed in Hunter Guild dialogue or Quetzal whistle messages
- Collection Log pages you have opened, retained separately for each account
- Quest states
- Achievement Diary task counts, tier completion, and separately claimed rewards
- Current Slayer assignment, remaining count, assigned area, points, and task streak
- Bank, inventory, and equipment snapshots with freshness metadata
- Grand Exchange offers and RuneLite price estimates
- A player appearance descriptor and compact local-player model for private avatars

It deliberately does **not** export world number, coordinates, FPS, animation
state, account credentials, Discord details, or other client telemetry.

## Where the file lives

After logging in, the plugin writes:

```text
%USERPROFILE%\.runelite\plugin-data\gielinor-profile-sync\latest.json
```

It also keeps one account-named JSON file in the same directory. Output stays
local; this plugin performs no network requests. A separate, user-controlled
sync tool may copy `latest.json` to a private service or repository.

RuneLite automatically moves existing exports from `.runelite/gielinor-profile-sync`
to this directory. Update any external sync tool to use the new path.

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
