# Magical Map

A physical atlas for Minecraft 1.21.1 and NeoForge 21.1.248. Join real vanilla maps,
chart the world through exploration, and navigate with player heads, owned villages,
your deployed C.A.M.P. and private landmarks.

**0.2.3.** Install matching versions
on client and server. Village Deed, C.A.M.P. and EMI integrations are optional.
0.2.1 fixes a client crash when clicking a map icon before any location was selected.
0.2.2 fixes the sidebar on short screens (1080p at GUI scale 4, ultrawides at scale 5):
the location list and the destination panel no longer draw over each other. When the
window is under 301 GUI rows tall the list fills the sidebar until you select a location,
whose details then take its place with a **Back to list** button. It also fixes operator
teleport refusing any destination whose chunk was not already loaded ("Destination terrain
is unavailable"); a destination saved on disk is loaded for the arrival, and terrain is
still never generated. Your own landmarks are now teleport targets for operators; 0.2.3
puts Edit and Delete on their own row beneath Teleport so the three no longer overlap, and
folds sheets that chart the same square into one, so the sheet count matches what you see.

![The atlas with two player heads, village, camp and landmark markers](devtools/verification/0.1.0/atlas.png)

[Travel view](devtools/verification/0.1.0/travel.png) ·
[Verification evidence](devtools/verification/0.1.0.md)

## Make and expand your atlas

Use a **cartography table**:

| First slot | Second slot | Result |
|---|---|---|
| Filled map | Book | Magical Atlas containing that map |
| Magical Atlas | Another filled map | Bind that sheet into the atlas |
| Magical Atlas | A filled map of a cell the atlas already charts | Fold it: its charted pixels join that sheet, the map is consumed, the count stays |
| Magical Atlas | Shears | Recover the last sheet; the remaining atlas stays in the first slot |

The atlas sits in its own **Magical Map** creative tab beside the vanilla tabs, and in
Tools & Utilities. With **EMI** installed, the three table operations above appear under a
**Cartography Table** category: look up the atlas's recipes or uses as with any item. The
table's behaviour is a menu extension, not a crafting recipe, which is why a recipe viewer
needs this plugin to show it.

An atlas holds up to **64 sheets, one per cell**: a cell is one dimension, one scale and one
128-block map square. Two maps started in the same square get different IDs but chart the
same blocks, so binding the second folds it into the first (a pixel the atlas already
charts is kept; banner markers on the folded map are not carried over). Duplicate copies of
the same map, which share its ID, are refused without consuming either input. An atlas made
before 0.2.3 that charts a cell twice folds the moment you hold it, and chat says how many
sheets folded. Shears lose one durability per extraction
in survival; creative shears do not wear. An empty atlas can accept maps again.

Prepare sheets with ordinary cartography: paper expands a map's coverage, an empty
map makes a copy, and a glass pane locks its terrain. Recover a bound sheet before
processing it, then bind the result again. Existing names and map components survive
binding and extraction. The atlas retains the original map IDs, so copies share
exploration exactly as vanilla maps do. Giving someone a copied chart shares that
geographical knowledge.

Exploration updates while you hold the atlas in either hand. Adjacent sheets meet
in one continuous view. Detailed sheets draw above broader sheets; uncharted pixels
remain transparent parchment. UI zoom magnifies existing detail and does not expand
coverage. Locked maps retain their terrain image while live location markers move.
Vanilla map limitations, including the Nether's ceiling-map appearance, still apply.
No cave terrain or automatic route finding is provided; underground landmarks retain
their recorded height.

## Navigate

- **Offhand or main hand:** the travel map appears in the upper right. Movement,
  mouse-look and ordinary gameplay controls remain available.
- **Use the atlas or press M:** open the full atlas. Drag to pan, scroll to zoom,
  and use Recenter to return to yourself. Keys can be rebound in Controls.
- **N:** toggle the travel map: a small map of your surroundings, 384 by 288 blocks, tucked
  into the top-right corner while you hold the atlas. Nothing but the map; your heading and
  coordinates are on Azimuth's bar. A bought village on the atlas says who owns it
  ("Owned by Jdrum12").
- Select a marker or a location in the list. **Track destination** keeps its name,
  horizontal distance and compass direction in the travel view. Your own head has
  a live heading indicator. Overlapping icons show a count; clicking cycles them.
- Use the dimension control to switch between dimensions represented by sheets or
  known locations. Dimensions have separate coordinate spaces.
- **Mark current position** records your current position, including depth. Right-click
  a charted point on the map to mark that point; its height is explicitly unknown.
  Give it a name, icon and color. Select your landmark to edit or delete it.

Personal landmarks are private player data saved in this world, with a limit of 128
per player. They survive dropping or replacing an atlas. Another player using your
atlas receives its map sheets, not your private landmarks.

Connected players appear as their skin heads. Village Deed's owned villages are
public markers at the centre each deed records. Only your own C.A.M.P.
appears; its owner ledger keeps it visible
when its chunk is unloaded. It disappears when packed and follows redeployment.
Active C.A.M.P. controls and terrain ownership remain entirely with C.A.M.P.

## On the Azimuth bar

With [Azimuth](https://github.com/the-rusty-shackleford/minecraft-azimuth) installed, the atlas
puts what it knows on the bar across the top of the screen **while an atlas is in your
inventory**: bought villages, your own C.A.M.P. and your own landmarks, within Azimuth's range
(256 blocks by default) and fading in over its last stretch. Players reach the bar through
Azimuth itself, never through the atlas. Without an atlas in your pockets the bar shows players
and compass points only. The atlas is an `AzimuthProvider` (`magicalmap:atlas`) relaying every
registered location provider but the players'; each bearing's id is the provider's id and local
id joined, so a place keeps one identity across atlas and bar. A provider that fails drops from
the bar and the atlas alike, on its own, logged once ("Atlas provider failed"); the others stay.
Azimuth is optional: without it nothing changes.

## Operator teleport

Operators with permission level **2 or greater** see **Teleport (operator)** for
owned villages, available camps and their own landmarks. Selecting an icon does not teleport you.

The server checks permission again on every request, resolves the location's current
identity, and searches for clear space on solid, non-hazardous ground. Dismount first.
Missing terrain, removed claims, transforming camps and unsafe arrivals are refused.
An explicit teleport may load an existing destination chunk; it does not generate a
new destination or place platforms. Normal location queries do not load chunks.
Your own landmarks are teleport targets too, for operators; other players' heads are
navigation destinations only.

## Extension protocol

See [the provider contract](docs/location-providers.md) and the
[accepted design](knowledge/decisions/D-0001.md).

The immutable JDK-only domain lives in `src/domain`. `LocationProvider` supplies
viewer-filtered snapshots and re-resolves actions. Integrations register through
`RegisterLocationProvidersEvent` on the NeoForge event bus at server startup.
Location kinds and icons use namespaced IDs; new providers do not require editing a
central kind enum. The icon identifies a registered item, with a map fallback if absent.

The atlas, table integration, world-saved landmarks, networking and rendering live
in separate adapters under `src/main`. Protocol version 1 requires matching peers.
Sessions are tied to the actual held atlas and its contents. Unequipping, replacing,
disconnecting or stopping the server clears their authorization and texture caches.

Village Deed 2.0.0 and C.A.M.P. 0.2.0 currently have no published API artifact used
by this project. Their bridges validate the installed Java signatures once at server
startup, using reflection confined to `integration/OptionalLocations.java`. Neither
mod is embedded. Incompatible signatures disable the affected bridge with a log
message. Future mods should implement the public provider port directly.

## Verification and development

Java 21 is required. The Gradle wrapper is included.

### Interactive playtest

```sh
bash devtools/playtest.sh
```

This opens a muted, hands-on course with operator access and creative mode. Your
offhand holds a partly explored two-sheet atlas; hotbar slot 1 holds a seven-page
field guide. Walk east across the bridge to reveal the frontier, track the mine,
create and edit landmarks, and try village/C.A.M.P. teleports. A moving **Surveyor**
simulates another server player without running a second rendering client. The
village is a small built fixture with a real Village Deed claim, not a generated
village discovery test. Spare cartography supplies are in your inventory.

Press **M** for the atlas and **N** for the travel map. The cartography table is at
`6, 64, -7`; `/tp @s 4 64 -8` returns you to the starting area. The launcher leaves
all controls with you and stays open until you quit. It opens a normal desktop
window using the native GPU driver, clears software-rendering overrides, and
refuses a software OpenGL renderer. The interactive FPS cap is 120.

The configured instance at `run/playtest/` is retained for future testing.
Launching again resumes your world, inventory, explored terrain and landmarks.
Only `bash devtools/playtest.sh --reset` rebuilds the practice course. The equivalent
Gradle option is `-PplaytestReset`; `--resume` remains an explicit resume shortcut.
The first launch requires
the three integration jars below and a template at
`run/world/level.dat` (create it with `./gradlew runGameTestServer`). Shader/runtime
files are copied from the booth setup below on a fresh launch. The launcher checks
host processes; run it from your desktop with no other Minecraft client running.
Direct `runPlaytest` is available for an already managed hardware display.

### Automated gates

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test runGameTestServer jar
```

The plain JUnit tests have no Minecraft dependency. Real-server GameTests exercise
actual cartography menu clicks, vanilla map operations, item components, private
persistence, authorization and safe arrival. Put actual Village Deed 2.0.0, Thief
1.2.4 and C.A.M.P. 0.2.0 jars in ignored `devtools/integration/` to run the bridge
tests. Their absence is explicitly reported as missing integration coverage.

The full client booth requires those integration jars. Put optional shader/runtime
jars in `run/booth/mods/`, a shader pack in `run/booth/shaderpacks/`, and its selection
in `run/booth/config/iris.properties`. With host process visibility and a working
desktop `DISPLAY`, run:

```sh
bash devtools/booth.sh
```

The launcher refuses another non-Gradle Java process, reuses an existing Xephyr or
chooses a free display, mutes master volume, and enforces a timeout. The booth walks
through real client clicks and packets, captures the rendered UI, and closes itself.
Review screenshots under `run/booth/screenshots/`; assertions alone do not establish
visual quality. No personal launcher instance or live server is modified.

`check` includes domain, server and client gates. `-PskipGameTests` and `-PskipBooth`
are focused development shortcuts, not complete validation. The server fixture
resets only this repository's disposable `run/world`. Test mods and integration jars
are excluded from the production jar.

Copyright Rusty Shackleford and nfx. AGPL-3.0-or-later.
