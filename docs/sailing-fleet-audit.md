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

## Decoded names and controlled active-vessel proof

RuneLite 1.12.35 publishes Sailing boat, core-component, and facility DB tables
through its public client API. Schema 2 resolves signals by the table type ID or
customisation-order column and exports a name only when all matching rows agree.
Indexed lookup has a bounded table-scan fallback. The original numeric value is
always retained and failed, ambiguous, or unsafe labels remain explicit.

RuneLite also publishes three persistent name option values for every owned
boat, the matching boarded-boat values, and the ordered prefix, descriptor and
noun table. Schema 3 decodes those exact options and rejects labels outside a
small display-safe character allowlist.

The active vessel is confirmed only when all of these agree at the same client
observation: the player-on-personal-boat flag, the last-personal-boat slot, that
slot's ownership, its boat type, and all three name option values. Any mismatch
remains unresolved and blocks a vessel portrait.

The following are intentionally not inferred:

- facility effects beyond the reviewed RuneLite DB name;
- open or missing slots (a zero hotspot is retained as observed data, not
  treated as a recommendation to build something);
- crew assignments;
- a cargo hold that RuneLite has not loaded.

No bank item is treated as proof that a component or facility is installed.
Future slices may add effects and compatibility rules only from reviewed,
versioned game data or another authoritative public source. Vessel portraits
remain explicit hotkey captures and use the same central-world privacy crop as
character history; automatic history captures never become fleet portraits.
