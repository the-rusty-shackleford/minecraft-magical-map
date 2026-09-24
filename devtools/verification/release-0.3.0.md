# Release verification — 0.3.0

2026-09-24. The atlas implements Azimuth's AzimuthLocation protocol (D-0007): with Azimuth
installed, a viewer carrying an atlas gets every place their atlas would list except the players
on the bar across the top of the screen, within Azimuth's range and in their dimension. The
bridge is compiled against `com.chunkworks.azimuth:azimuth:1.0.0` from mavenLocal, optional at
runtime, registered only past a `ModList` check. `AtlasServer.places(provider, viewer)` is the
contract check and failure isolation the atlas always had, factored out so the bar drops a
failing provider on its own, as the atlas does, instead of losing every atlas place.

Full `./gradlew clean build` on the release tree, Xephyr `:7`, llvmpipe, muted:

- JUnit: 18 tests, 0 failures, unchanged.
- `runGameTestServer` with Azimuth 1.0.0 from mavenLocal plus Village Deed 2.0.0, Thief 1.2.4 and
  C.A.M.P. 0.2.0 from `devtools/integration/`: all 20 required tests passed. The new
  `atlasBearingsFollowAzimuthsProtocol` ran (no "INTEGRATION NOT RUN" line): without an atlas the
  bar gets nothing from the atlas; with one, a test destination 100 blocks off is on the bar and
  one 400 blocks off is not; nothing under `magicalmap:players/`; every bearing's provider is
  `magicalmap:atlas`; icon, colour and position carried over.
- `runPhotoBooth` with Sodium, Iris, Complementary Unbound 5.8.1 and Azimuth loaded: COMPLETE,
  37 checks. After the operator teleport the bar's places are
  `magicalmap:atlas/villagedeed:villages/minecraft:overworld/structure:8`, the two fixture
  landmarks, the booth's own landmark and `mobilecamp:camps/<owner>`; no `magicalmap:players`
  entry. The booth then faces west, where those lie 90 to 170 blocks off, and photographs the bar
  ([bar](0.3.0/15-azimuth-bar.png), [arrival](0.3.0/08-arrival.png)). No "Atlas provider failed"
  or "Atlas integration unavailable" line.
- Rerun the same day against Azimuth's restyled bar (its D-0003; jar sha1
  `3fe4b3c300fc3c380d4eebc58b4fde0402ee659c` from mavenLocal): 20 GameTests passed again, the
  booth COMPLETE with 37 checks twice, the photos above replaced. Judged by eye at 4x and 8x
  under the shaders: the outlined bar with its highlight, the village bell over its dot on top
  at the centre (the village is underfoot, which Azimuth draws dead ahead), the ford's boat over
  its blue dot and the camp's campfire over its green dot peeking out to the bell's left (they
  lie 4° and 7° left, 92 and 152 blocks off), the booth waypoint's pickaxe over its gold dot to
  the right (7°, 131 blocks), and the Surveyor as Azimuth's small outlined far dot at 40 %
  (144 blocks, past the fade's floor at 125) instead of a head; the iron mine's pickaxe (9° left,
  164 blocks) is under the cluster. The first rerun showed the bell hidden under the boat: Azimuth
  drew nearest first and so put the nearest underneath; it now draws farthest first.
- Jar `magicalmap-0.3.0.jar` sha1 `4abff11db9cfe56d00c60296ffe998b9312d2cad` (119398 bytes); of
  Azimuth only the mod's own `integration/azimuth` classes inside, nothing of Azimuth, Village
  Deed, Thief or C.A.M.P.

The cartography-screen flake: the day's first two booth runs with Azimuth loaded never saw the
`CartographyTableScreen` within phase 2's 20 s cap (the FAIL documented since 0.2.3). Phase 1 now
logs the client's position, screen and the table block when it uses the table, and phase 2 logs
the client's screen and the server's menu state at 10 s and 19 s. The next eight runs (six, then
the two reruns for the restyled bar), logged the same way, all opened the screen within two
seconds of the peer's join, so the cause is not pinned; the next failure will carry the
evidence.

Not verified: the bar on the live server (Azimuth is unreleased; both ship together as pack
1.62.0 on Rusty's go, with the Locator Bar's override jar dropped). The jar is unchanged by the
restyle: the look is Azimuth's, and only the photos here changed.
