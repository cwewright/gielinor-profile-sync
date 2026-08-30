# Character capture schema v1

Each completed history scene is a PNG and JSON pair with the same UUID stem in
`.runelite/gielinor-profile-sync/captures/pending`. The PNG moves into place
first; the JSON manifest moves last and is the ready marker for a sync tool.

## Identity and source

- `schemaVersion`: capture contract version, currently `1`
- `captureId`: random bundle identifier; it is not an account identifier
- `capturedAt`: UTC capture timestamp
- `source`: plugin identity/version and local-only capture mode

The manifest deliberately omits the RuneScape display name. A user-controlled
sync process associates a pending bundle with its authenticated profile.

## Scene context

- `context.trigger`: `manual` or explicitly enabled `scheduled`
- `context.sceneTag`: broad `bank`, `adventure`, or exact-slot `vessel` scene tag
- `context.skillTag`: optional skill name derived from a recent XP increase
- `context.locationTag`: optional `region-<id>` tag for a coarse 64-by-64 map region
- `context.fleet`: present only for a manual `vessel` capture. It contains the
  confirmed `boat-slot-1` through `boat-slot-5` identity and optional sanitized
  game-DB name/class labels. The capture is refused unless the persistent slot,
  aboard flag, boat type, and all three name signals agree.
- `camera`: yaw, pitch, scale and capture-frame dimensions. Pixel fields use the
  delivered screenshot coordinate space; `logical*` fields retain RuneLite's
  canvas coordinates, and `pixelScaleX`/`pixelScaleY` record independent display
  scaling. `camera.captureWidth` and `camera.captureHeight` match the saved PNG.
- `character`: animation, pose frame and orientation at capture time
- `appearance`: equipment/kit descriptor, stable fingerprint and compact model
- `framing`: pixel and normalized character bounds, quality and suggestions

No exact coordinate, plane or world number is recorded. The optional coarse region
tag is intended for matching a scene to dashboard art without storing a tile-level
position. Scheduled capture is off by default and only runs after explicit opt-in.

## Image integrity and privacy

`image` names the PNG and records its dimensions, byte length, SHA-256 digest
and difference hash. The difference hash lets a consumer reduce near-duplicate
scenes when selecting a varied recent gallery.

In resizable mode the saved central scene crop excludes standard chat, minimap
and action-tab regions. Fixed mode uses RuneLite's world viewport. Captures are
blocked while bank contents or a right-click menu are open. The plugin never
uploads captures; the player should still review an image before choosing to
share it because the game scene itself can contain other visible characters. The
local pending queue protects two bank scenes and the newest scene for each skill
tag and confirmed vessel slot before filling remaining capacity newest-first.
