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
- `achievementDiaries`: schema `2` adds task progress by region and tier.
  Each tier includes `completedTaskCount`, `totalTaskCount`, `tierComplete`,
  `rewardClaimed`, and `signalStatus`. `complete` remains a compatibility alias
  for tier completion; `value` retains the raw reward value. Missing signals
  remain unknown instead of being treated as incomplete.
- `slayer`: server-backed assignment state, advertised by the `slayerTask` capability.
  Includes `taskLoaded`, `taskFromCache`, `taskLastSeenTimestamp`, `status`
  (`live`, `none`, or `unavailable`), points, and streak. A live `task` includes
  its monster name when known, remaining and original counts, master ID, and
  required assignment area when present. An unknown identity is not guessed.

- `collectionLog`: observed Collection Log pages and unique-item totals, advertised
  by `collectionLogPageObservations`. Coverage is `observed-pages-only`: unopened
  pages remain unknown. Item slots include their observed ownership and quantity.
  Observations persist separately for each account; other players' house logs
  are ignored. Bank contents are not used to infer Collection Log progress.

- `hunterRumours`: observed Hunter Guild assignments, rare-piece completion, and
  last-seen timestamps. `coverage` is `observed-dialogue-and-whistle`; unobserved
  masters and completion totals remain unknown. Restored observations are marked
  `fromCache`. Raw chat and exact player coordinates are not exported.

- `playerOwnedHouse`: retained observations of furniture and room layouts, captured
  only with building mode active and the house exit portal observed. Room positions
  are relative to that portal, not world coordinates. A retained inspection has
  `fromCache: true`; unvisited or unobserved furniture is not inferred.

## Items

`inventory`, `equipment`, and `bank` each include `loaded`, `value`,
`itemCount`, and slot-keyed `items`. The matching top-level `*FromCache` and
`*LastSeenTimestamp` fields tell consumers whether RuneLite currently had that
container loaded. Consumers must not interpret an unloaded container as empty.

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
