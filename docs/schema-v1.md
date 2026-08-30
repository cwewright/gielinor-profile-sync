# Profile schema v1

`latest.json` is a complete snapshot. Consumers should ignore unknown fields so
minor plugin updates remain forwards-compatible.

## Identity and freshness

- `schemaVersion`: integer schema contract, currently `1`
- `version`: plugin version
- `timestamp` and `timestampIso`: snapshot creation time
- `rsn`: the local player's display name
- `capabilities`: sections this plugin knows how to provide

## Progression

- `skills`: keyed by display name with `level`, `boostedLevel`, and `xp`
- `quests`: totals and entries keyed by RuneLite quest identifier
- `achievementDiaries`: a nested versioned diary section. Schema 2 separates
  each tier's `rewardClaimed`, `tierComplete`, `completedTaskCount`, and
  catalogue-backed `totalTaskCount`. The legacy `complete` field mirrors the
  reconciled tier-completion result and `value` remains the reward-varbit value.
  Missing client signals are explicit JSON `null` values with
  `signalStatus: "partial"` or `"unavailable"`; they must not be interpreted as
  zero progress. A confirmed completion or claimed reward is monotonic positive
  evidence and reconciles that tier's completed count to its catalogue total.
  Numeric counts never identify which specific tasks were completed.

  Schema 2 is produced by RuneLite 1.12.35 varbits and advertises the additional
  `achievementDiaryTaskProgress` capability. The twelve stable region keys are
  `ardougne`, `desert`, `falador`, `fremennik`, `kandarin`, `karamja`,
  `kourend_kebos`, `lumbridge_draynor`, `morytania`, `varrock`,
  `western_provinces`, and `wilderness`. Karamja intentionally uses RuneLite's
  older `ATJUN_*` completion/reward signals with the `KARAMJA_*_COUNT` signals.
  Region and account completion aggregates are `null` whenever any required
  tier-completion signal is unknown, so unavailable data never becomes `0/4`
  or `0/48`.

  Plugin 0.3.1 and earlier emitted only reward-derived `complete` and `value`.
  Consumers should use nested `achievementDiaries.schemaVersion` and the
  `achievementDiaryTaskProgress` capability rather than interpreting a legacy
  reward zero as confirmed zero task progress.

- `slayer`: a versioned, freshness-aware snapshot of the server-backed Slayer
  assignment values RuneLite uses for its task counter. When `taskLoaded` is
  true it can include the game task ID and resolved monster label,
  remaining/original count, optional required assignment area, master ID,
  points, and streak. A task with no safe database label keeps its numeric ID
  and count but marks `identityKnown: false`; unavailable values remain `null`.
  The `slayerTask` capability never derives an assignment from chat, kills,
  inventory, exact coordinates, or other live telemetry.
- `hunterRumours`: freshness-aware observations of the current Hunter Guild
  assignment and any master-specific assignments encountered in Hunter Guild
  dialogue. RuneLite has no complete server-backed assignment variable, so the
  exporter also listens for the Quetzal whistle's own current-rumour message.
  `coverage: "observed-dialogue-and-whistle"` is intentionally partial:
  masters not observed are unknown, `completionCount` remains `null`, and the
  rare-piece message can mark the current assignment complete without guessing
  how many creatures were caught. Retained observations are explicitly marked
  `fromCache` after a restart.
- `collectionLog`: exact slot state for Collection Log pages observed through
  RuneLite's rendered interface

### Collection Log observation boundary

RuneLite exposes individual Collection Log slots while a page is rendered.
`collectionLog.status` is therefore `partial` after at least one page has been
visited and `unavailable` before that. `coverage` is always
`observed-pages-only`; an absent page means unknown, never zero progress.

Each entry under `collectionLog.pages` records the page title, observation
timestamp, page-local counts, and item-ID-keyed slots with `itemName`,
`obtained`, and observed `quantity`. Shared items may appear on multiple pages,
so page-slot totals are not the same as unique-item totals. Bank contents are
not consulted when producing this section. Observations persist in each
account's own local export, so switching RuneScape accounts cannot replace one
account's log with another's.

## Items

`inventory`, `equipment`, and `bank` each include `loaded`, `value`,
`itemCount`, and slot-keyed `items`. The matching top-level `*FromCache` and
`*LastSeenTimestamp` fields tell consumers whether RuneLite currently had that
container loaded. Consumers must not interpret an unloaded container as empty.

## Sailing fleet

When `sailingFleet` is advertised in `capabilities`, `sailing` contains a
versioned account-scoped fleet snapshot. `fleetLoaded`, `fleetFromCache`, and
`fleetLastSeenTimestamp` describe the persistent boat signals. Each owned boat
has a stable exporter slot ID, raw type/component identifiers, stored hull
hitpoints, thirteen raw hotspot values, and an independently freshness-marked
`cargo` container.

Schema 2 fleet exports retain every raw value and add labels resolved at
capture time from RuneLite's Sailing game DB. Failed, unavailable, or ambiguous
lookups remain explicit. A consumer must not turn zero into a named missing
facility and must not treat unloaded cargo as empty. Raw boarded-boat signals
support controlled active-slot correlation; crew assignment remains unknown.

Schema 3 additionally reads each persistent boat's three name-option values and
resolves them only through RuneLite's ordered Sailing name table. `activeBoat`
is `confirmed` only while the player is aboard their own boat and the persistent
slot, live or stored type, and all three boarded-name signals agree. Otherwise it
is explicitly `unresolved`. This is the sole correlation used by vessel portrait
captures; a generic Sailing scene is never assigned to a boat slot.

## Appearance

`appearance` contains the raw RuneLite player-composition descriptor needed by
a renderer: gender, five body colour indexes, transformed NPC ID, the raw
equipment array, and named kit slots. Each slot labels its decoded content as
`item`, `kit`, or `empty`.

When RuneLite has a local-player model ready, `appearance.model` contains a
compact, versioned copy of that rendered low-poly mesh. Vertex positions, face
indexes, per-corner Jagex HSL colours, transparency, and texture identifiers are
included. Consumers can rotate and project this mesh without downloading game
models or sending appearance data to a rendering service. Textured faces may be
shown with their model colour when a consumer does not ship game textures.

The appearance section is not a screenshot and never contains credentials or
location data. A website should let the player explicitly choose when a newly
exported look replaces their saved avatar.

## Player-owned house

`playerOwnedHouse` remains authoritative only after the player enters their own
house in building mode and the exit portal is observed. Schema 2 retains the
schema-1 flat `furniture` list and adds `rooms`, grouping furniture into the
8-by-8 instance chunks exposed by RuneLite. Room coordinates are relative to
the exit-portal room; furniture coordinates are room-local tiles. No world
coordinate, player position, nearby player, or visitor-house claim is emitted.

Each room also includes its generic instance-template region/chunk reference
and rotation. Those references are game-definition evidence for a future room
catalogue, not the player's location. Until independently catalogued,
`roomType` is `null` and `identityKnown` is false.

## Character history captures

`characterCaptures` in `capabilities` means the plugin can create separate,
user-triggered PNG/JSON bundles. These captures are not embedded in
`latest.json`; see `capture-schema-v1.md`.
