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
- `achievementDiaries`: reward-tier completion by region

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

## Character history captures

`characterCaptures` in `capabilities` means the plugin can create separate,
user-triggered PNG/JSON bundles. These captures are not embedded in
`latest.json`; see `capture-schema-v1.md`.
