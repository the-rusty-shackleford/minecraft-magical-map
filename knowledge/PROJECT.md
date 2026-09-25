# Magical Map

Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21. New mod, `magicalmap`, in
`minecraft-magical-map`. Version 0.1.0 is the first release. Rusty approved the
interactive test and authorized release on 2026-09-20.

Version 0.2.0 (2026-09-22, Rusty's request and release authorization in one): the
atlas gets the mod's own creative tab, and an EMI plugin shows the three
cartography-table operations under a "Cartography Table" category. See
[D-0002](decisions/D-0002.md). The operations are data in the domain
(`AtlasRecipes`, JUnit-tested); the plugin only loads when EMI is present.

Version 0.2.1 (2026-09-22): Rusty's client crashed on clicking a map icon with nothing
selected: the icon's id list is immutable and was asked `indexOf(null)`. The cycling and
pick logic moved to the domain (`Selection`, JUnit-tested with the null case); the canvas
and screen call it.

Version 0.2.2 (2026-09-22): Rusty's ultrawide at GUI scale 5 is 258 rows tall and the
sidebar assumed 301 (list from row 128, detail block bottom-anchored 142 up), so the list
and "Choose a destination" overlapped. Any 1080p window at scale 4 (270 rows) had it too;
the booth's 360 rows never showed it. See [D-0003](decisions/D-0003.md). The booth now
also photographs the atlas at 720p scale 3, the 240-row worst case.

Also in 0.2.2: Rusty's teleport to their own village answered "Destination terrain is
unavailable". The arrival check asked for the chunk with create=false, which only finds
chunks already in memory, so every destination the player was not standing near was
refused. It now reads the chunk from disk, accepts it only at full generation status, pins
it with the vanilla post-teleport ticket and loads it; terrain is still never generated.
See [D-0004](decisions/D-0004.md). Three GameTests cover in memory, on disk and never
generated. Rusty then asked for landmarks to be teleport targets like villages; they
resolve for their owner and carry `teleportable` (D-0005), with a GameTest. That put
Teleport, Edit and Delete on one row; 0.2.3 gives Edit and Delete a third row (D-0003
amendment).

Version 0.2.3 (2026-09-23): besides the third row, Rusty's atlas said nine sheets and showed
two regions. A sheet's identity was its map id; two maps started in the same grid square get
different ids and identical bounds. Identity is now the cell (dimension, scale, centre): a map
of a charted cell folds into that sheet at the table, and a held atlas charting a cell twice
folds on the spot. See [D-0006](decisions/D-0006.md). Also fixed: the atlas screen laid out
its sidebar only when rendering, so a click between two frames could test against the
geometry of a stale selection, and widgets changed by a click showed only at the next frame;
the booth hit both as flakes on the short layout under llvmpipe (a pick right after Back to
list landed on a hidden list; a check six ticks after a pick found the buttons still hidden).
Layout now runs before every click and scroll and again after every click. Gotcha for any
future map work: client map data has no centre (`createForClient`), so cells can only be
told apart on the server (D-0006).

Rusty selected an atlas built from real vanilla map sheets, revealed through
exploration with a meaningful cartography-table workflow. See
[D-0001](decisions/D-0001.md). Live players, owned villages, the viewer's deployed
C.A.M.P. and personal landmarks share a location-provider contract.

On 2026-09-20, six JDK-only JUnit tests and twelve real-server GameTests passed.
The muted client booth passed with actual Village Deed 1.0.0, Thief 1.2.4 and
C.A.M.P. 0.2.0 jars, Sodium/Iris and Complementary Unbound 5.8.1. Rendered atlas,
travel view, player heads, village actions and landmark editor were inspected.
See [verification evidence](../devtools/verification/0.1.0.md) for the scope,
screenshots, commands and limits.

The clean release build passed six JUnit tests, twelve real-server GameTests and
the full muted shader-client walkthrough on hardware OpenGL. The production jar
is byte-identical to the reviewed candidate. See
[release verification](../devtools/verification/release-0.1.0.md).
The optional integration bridges use validated reflection until their source mods
provide a published API. The public provider protocol is documented in
[location-providers.md](../docs/location-providers.md).

The hands-on course is launched with `bash devtools/playtest.sh`. It has a separate,
retained world, a field guide, real integration fixtures and a moving simulated
second player. See the README for the route and controls. It is separate from the
automated booth and is never a prerequisite that blocks unattended `check` runs.
On 2026-09-20 its Java compilation and launcher shell checks passed. The actual
shader client reached `atlas playtest: READY` with two sheets, two player markers,
an active camp, village teleport and the field guide; the simulated player's
connection remained active beyond 40 seconds. The starting travel view was
visually inspected. Rusty subsequently approved the hands-on result.

0.3.0 (2026-09-24): the atlas implements Azimuth's AzimuthLocation protocol (D-0007):
`integration/azimuth/AtlasBearings` relays every registered provider but the players' to the
bar for viewers carrying an atlas, in their dimension within Azimuth's range, under the id
`magicalmap:atlas` with `<provider>/<id>` as the place's id; `AzimuthBridge` registers it only
when `ModList` finds Azimuth, so the entry never names an Azimuth class. Azimuth is compiled
against from mavenLocal (`com.chunkworks.azimuth:azimuth:1.0.0`, `./gradlew publishToMavenLocal`
in its repo) and optional at runtime; the gametest server and the booth load it. `AtlasServer`
now exposes `providers()`, `server()` and `places(provider, viewer)`, the contract check and
failure isolation the atlas always had, factored out so the bar drops a failing provider on its
own too (D-0007). The integration GameTest and the booth's new bar photo (`15-azimuth-bar`: after
the arrival the booth faces west, where the landmarks, the ford, the camp and the peer lie)
verify it. The booth's phase 1 and 2 now log the client's position, screen and the server's
menu state, for the cartography-screen flake (two of the day's first three runs never saw the
screen within the 20 s cap; the next runs, logged the same way, all did). Later the same day
Azimuth's bar was restyled before its first release (Azimuth D-0003: outlined bar, badges,
framed heads, dots under sprites, chevrons, far players as dots); the GameTests and the booth
were rerun against the restyled jar and the bar photo replaced, the jar itself unchanged. That
rerun found Azimuth drawing the nearest marker underneath (the village bell underfoot hidden
by the ford's boat), fixed in Azimuth. Places clustered within 20° of each other overlap on a
102-pixel bar; the nearest is on top. **Released 2026-09-24** (tag v0.3.0) with Azimuth 1.0.0
as pack 1.62.0 on Rusty's "Release and deploy"; deployed the same hour, no "Atlas integration
unavailable" line.

0.2.4 (2026-09-24): Village Deed 2.0.0 (Chunkworks, `com.chunkworks.villagedeed`) replaced
nfx's 1.0.0 in pack 1.61.0 and the reflective bridge in `integration/OptionalLocations`
found no `com.nfx.villagedeed.village.VillageClaims`; the server logged "Atlas integration
unavailable: Village Deed" and atlases lost owned villages (none existed yet). The bridge
now binds to `Claims`, `Claims$Claim` (`id`, `name`, `centre`, `deed`) and `Deed.owner`, and
places the marker at the centre the deed records instead of the start chunk's middle at
y 0. The integration GameTest and the booth's village fixture create a real 2.0.0 claim
(`VillageId("structure", <chunk>)`, `Deed.of(owner)`, a centre, a price). The jar in
`devtools/integration/` is 2.0.0. Village Deed's own `api` package is the way forward for a
non-reflective binding.

Rusty's first interactive test reported severe stutter: its launcher had inherited
the automated booth's software-rendering overrides. The interactive launcher now
uses the desktop GPU, rejects software OpenGL, and defaults to a 120 FPS cap.
Resuming is now the default; `--reset` explicitly rebuilds the practice course.
Rusty asked to retain this configured instance for future testing. The corrected
launch resumed the saved course with the hardware renderer and logged 60 FPS
with the same shader pack. Compilation and shell checks passed. This corrects
the original playtest's rendering environment; it is not a live-server release.

0.3.1 (2026-09-24, late): villages in the atlas say who owns them ("Owned by Jdrum12"; the
bridge binds Village Deed's `Claim.ownerName()`), and the held atlas's travel view is a bare
minimap tucked into the top-right corner, 96 by 72 map pixels at 4 blocks a pixel (was 128 by
100 at 2, under a title, over a heading-and-coordinates line that Azimuth's bar now carries).
The tracked destination's name and distance stay under it (D-0008).

## Published and deployed — 2026-09-25, pack 1.64.0

Version 0.3.1 (D-0008: villages say who owns them; the bare corner minimap) is
[published](https://github.com/the-rusty-shackleford/minecraft-magical-map/releases/tag/v0.3.1)
and deployed through Mod Hub in pack **1.64.0**, replacing 0.3.0 on the server, with Backpacks+
0.4.0, Warehouse Manager 0.5.0 and Schnappviecher 0.1.2 on Rusty's go. Release asset, tested
jar and installed server jar match SHA-1 `fc2f3f5b9661edd8b1a264dc365fff6f65bea537`. The log
notes "magicalmap (version 0.3.0 -> 0.3.1)"; 20 TPS; parity clean. The server repo's
`knowledge/releases/pack-1.64.0.md` has the whole deployment.
