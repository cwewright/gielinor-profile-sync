# Sailing fleet export audit (RuneLite 1.12.35)

This audit is deliberately limited to public RuneLite 1.12.35 API/gameval
sources. The exporter does not inspect packets, client internals, scene
coordinates, the current world, or nearby players.

## State RuneLite can prove

RuneLite's generated `VarbitID` exposes five persistent player boat slots. For
each slot it supplies an `OWNED` signal, a numeric boat `TYPE`, numeric keel,
hull, sail, steering, teleport-focus, flag, brazier and trim values, stored and
maximum hitpoints, and thirteen hotspot/component values with corresponding
extra-data values. These are account-scoped values and do not require a live
location.

RuneLite's generated `InventoryID` also exposes one cargo-hold item container
for each of the five boat slots (`SAILING_BOAT_1_CARGOHOLD` through
`SAILING_BOAT_5_CARGOHOLD`). An item container can be exported only when the
client has loaded it. The plugin retains the last loaded container in memory
and can reuse the last local snapshot after a restart; cached cargo is marked
with its original last-seen timestamp.

## State this slice intentionally leaves unknown

RuneLite 1.12.35 does not publish a decoded vessel/facility model in its public
API. The generated values identify the state but do not provide a stable public
mapping from every numeric value to a display name or facility effect. This
slice therefore exports raw identifiers and lets consumers label them as
recorded component values, not named game objects.

The following are intentionally not inferred:

- the active vessel (a last-boarded value exists, but this audit did not prove
  a stable slot mapping suitable for an account contract);
- boat display names;
- decoded boat, component, or facility names/effects;
- open or missing slots (a zero hotspot is retained as observed data, not
  treated as a recommendation to build something);
- crew assignments;
- a cargo hold that RuneLite has not loaded.

No bank item is treated as proof that a component or facility is installed.
Future slices can add decoded labels only from a reviewed, versioned game-data
catalogue, while keeping the account snapshot limited to observed state.
