# Release verification — 0.3.1

2026-09-24, late. Rusty: villages should say who owns them; the held atlas's minimap is too
zoomed in and cluttered ("get rid of the coordinates … and the direction, and the Magical Atlas
part. Keep it just the minimap and tuck it into the corner"). D-0008.

Full `./gradlew clean build` on the release tree, Xephyr `:7`, llvmpipe, muted:

- JUnit: 18 tests, unchanged.
- `runGameTestServer` with Azimuth 1.0.0 from mavenLocal plus Village Deed 2.0.0, Thief 1.2.4
  and C.A.M.P. 0.2.0: all 20 required tests passed.
- `runPhotoBooth` with Sodium, Iris, Complementary Unbound 5.8.1 and Azimuth: COMPLETE,
  37 checks. The village fixture's claim lists as "Owned by Cartographer" (the booth player's
  name; three occurrences in the log, list and detail). The travel view
  ([photo](0.3.1/02-travel.png)) is the map alone in its frame at the top-right, 110 by 86, the
  map 96 by 72 at 4 blocks a pixel: the two sheets, the river, the booth's landmark and the
  village bell, the "N" of the map's own compass; no title, no heading, no coordinates. After
  the arrival ([photo](0.3.1/08-arrival.png)) the tracked destination's strip still sits under it
  ("Eastwatch Village, 0 blocks S"). Judged by eye at 2x.
- Jar `magicalmap-0.3.1.jar` sha1 `fc2f3f5b9661edd8b1a264dc365fff6f65bea537` (119397 bytes).
- Not verified: the corner frame beside Azimuth's bar on Rusty's ultrawide (at the game's
  minimum width they overlap by five pixels; at any ordinary width they are clear); the live
  server's villages reading "Owned by Jdrum12" (Village Deed 2.0.1 keeps the name).
