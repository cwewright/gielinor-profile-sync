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

  A tier has this forwards-compatible shape:

  ```json
  {
    "complete": null,
    "value": null,
    "rewardClaimed": null,
    "tierComplete": null,
    "completedTaskCount": null,
    "totalTaskCount": 10,
    "signalStatus": "unavailable"
  }
  ```

  Plugin 0.3.1 and earlier emitted only reward-derived `complete` and `value`.
  Consumers should use nested `achievementDiaries.schemaVersion` and the
  `achievementDiaryTaskProgress` capability rather than interpreting a legacy
  reward zero as confirmed zero task progress.

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
