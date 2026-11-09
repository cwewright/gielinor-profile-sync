# Gielinor Profile Sync

Export your character's progress, gear, and bank for personal dashboards and
planning tools. Gielinor Profile Sync keeps an account snapshot up to date as
you play, ready for a compatible companion app of your choice.

- Track skills, quests, diaries, Slayer assignments, and Hunter Rumours.
- Keep your last observed bank contents and Collection Log pages available between visits.
- Privacy-minded: exports stay on your PC, and the plugin makes no network requests.

## Use it with Sailor's Log

[Sailor's Log](https://osrs.cwew.org/) is an optional companion website that turns
your exports into a personal OSRS dashboard:

- Plan skill goals, quests, diaries, and your next Slayer assignment.
- Compare money-making routes and market prices, or plan meals from your banked ingredients.
- Explore minigames, house upgrades, travel routes, and Combat Achievement preparation.
- Browse observed Collection Log pages and follow your progress over time.
- Inspect supported house and boat exports, with unknown details clearly marked.

Install this plugin in regular RuneLite, sign in to Sailor's Log with Discord,
then use its [Windows companion download and setup page](https://osrs.cwew.org/#/sync).
The [step-by-step guide](https://github.com/cwewright/gielinor-profile-sync/wiki/Getting-started)
explains where to copy your Profile ID, create a device upload key, and connect your PC.
See the [website feature guide](https://github.com/cwewright/gielinor-profile-sync/wiki/Website-features)
for details and features still in preview.

The companion uploads only when you connect it. The RuneLite plugin remains a
local exporter and does not require a website account.

## What it exports

- Skill levels and XP, including Sailing
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
