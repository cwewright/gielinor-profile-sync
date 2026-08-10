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

- `context`: manual trigger plus broad `bank` or `adventure` scene tag
- `camera`: yaw, pitch, scale and capture-frame dimensions
- `character`: animation, pose frame and orientation at capture time
- `appearance`: equipment/kit descriptor, stable fingerprint and compact model
- `framing`: pixel and normalized character bounds, quality and suggestions

No exact coordinate, map region, plane or world number is recorded.

## Image integrity and privacy

`image` names the PNG and records its dimensions, byte length, SHA-256 digest
and difference hash. The difference hash lets a consumer reduce near-duplicate
scenes when selecting a varied recent gallery.

In resizable mode the saved central scene crop excludes standard chat, minimap
and action-tab regions. Fixed mode uses RuneLite's world viewport. Captures are
blocked while bank contents or a right-click menu are open. The plugin never
uploads captures; the player should still review an image before choosing to
share it because the game scene itself can contain other visible characters.
