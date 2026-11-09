# Gielinor Profile Sync

Bring your RuneLite character into a personal OSRS planning dashboard.
Gielinor Profile Sync keeps your progress, gear, and bank in a local snapshot as
you play. Connect the optional **Sailor's Log** companion to find your next goal,
put your supplies to work, and follow your progress between sessions.

[Open the dashboard](https://osrs.cwew.org/) ·
[Windows companion download](https://osrs.cwew.org/#/sync) ·
[Setup guide](https://github.com/cwewright/gielinor-profile-sync/wiki/Getting-started)

## Get connected

1. Install **Gielinor Profile Sync** from Plugin Hub in regular RuneLite, log in,
   and open your bank.
2. Sign in to Sailor's Log with Discord and download the Windows companion.
3. Copy your **Profile ID** and create a **device upload key** on the website's
   **Sync** page. Enter them in the companion, test the connection, and choose
   **Sync now**.

The website is free, with optional donations and no paid feature tiers. You do
not need to join a Discord server to use your personal dashboard.

## Your character and your next adventure

See your RuneLite character alongside suggested quests, useful diary errands,
and goals suited to your progress. Explore skills, Slayer, Collection Log pages,
and progress history from the same dashboard.

![Sailor's Log character dashboard with quest recommendations and an achievement diary goal](https://raw.githubusercontent.com/wiki/cwewright/gielinor-profile-sync/images/command-deck-2026-11-09.jpg)

## Find something worth cooking

Browse recipes by level, healing, training value, or ingredients you already have.
Compare dishes, follow their preparation steps, and scale a shopping list for
your next cooking session.

![The Cookbook showing recipe filters, bank-aware recommendations, and cooked karambwan details](https://raw.githubusercontent.com/wiki/cwewright/gielinor-profile-sync/images/cookbook-2026-11-09.jpg)

## Keep an eye on your supplies

Build a market watchlist and compare observed prices, trading volume, and price
history. The Money Making section also helps you explore earning routes suited
to your account.

![The market desk with a supply watchlist and seven days of yew log prices and trading volume](https://raw.githubusercontent.com/wiki/cwewright/gielinor-profile-sync/images/market-2026-11-09.jpg)

## Plan where to go next

Find activities, banks, and travel options on the Gielinor Atlas. Filter by skill
and access, compare destinations, and build a trip around the activities you
want to do.

![The Gielinor Atlas showing activity filters, bank locations, and travel options](https://raw.githubusercontent.com/wiki/cwewright/gielinor-profile-sync/images/atlas-2026-11-09.jpg)

These screenshots show the optional companion website. Visit the
[full feature guide](https://github.com/cwewright/gielinor-profile-sync/wiki/Website-features)
for quests, minigames, Construction, fleet planning, and more, including which
in-game companion features are still in preview.

## Your data, your choice

- Privacy-minded: the RuneLite plugin makes no network requests. Exports stay on your PC.
- The separate companion uploads only after you connect it; a website account is optional.
- Your last observed bank and Collection Log pages remain available between visits.

<details>
<summary>Exported data and file location</summary>

### What it exports

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

### Where the file lives

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

</details>

<details>
<summary>Development</summary>

Run the tests with:

```text
gradlew.bat test
```

Run the RuneLite development client with:

```text
gradlew.bat run
```

See [docs/schema-v1.md](docs/schema-v1.md) for the stable top-level contract.

</details>
