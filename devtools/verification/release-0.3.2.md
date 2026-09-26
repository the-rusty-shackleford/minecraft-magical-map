# Release verification — 0.3.2

2026-09-26. Rusty, on 0.3.1's corner map: "the minimap simplification is great but its still
too zoomed in. It needs to show more in the same amount of space." D-0009.

Full `./gradlew clean build` on the release tree, Xephyr `:70`, llvmpipe, muted:

- JUnit: 18 tests, unchanged.
- `runGameTestServer` with Azimuth from mavenLocal plus Village Deed 2.0.0, Thief 1.2.4 and
  C.A.M.P. 0.2.0: all 20 required tests passed (`run/gametest/config/fml.toml` and
  `run/config/fml.toml` carry `disableConfigWatcher = true` so the headless server does not
  starve on the desktop's inotify ceiling).
- `runPhotoBooth` with Sodium, Iris, Complementary Unbound 5.8.1 and Azimuth: COMPLETE. The
  travel view ([photo](0.3.2/02-travel.png)) is the same 110 by 86 frame at the top-right with
  the same 96 by 72 map, now at 8 blocks a pixel: the grid is one line per 128 blocks, and the
  booth's single 128-block sheet draws at half its 0.3.1 width, so the box holds twice the
  ground each way ([3x crop beside 0.3.1](0.3.2/02-travel-vs-0.3.1.png)). The camp, the peer
  and the booth player's own head, a few dozen blocks apart, now fold under one icon with a
  "3"; the bell stays its own icon; the heading dots still point south. After the arrival
  ([photo](0.3.2/08-arrival.png)) the tracked destination's strip still sits under the map.
  Judged by eye at 3x.
- Jar `magicalmap-0.3.2.jar` sha1 `4857a7f435cd7aaf9768d06822629ee1f02ab2ab` (119398 bytes).
- Not verified: a filled map. The booth fixture has one scale-0 sheet, so the photo shows the
  halving, not terrain across the frame; Rusty's atlas is scale-4 sheets (read off the box's
  `map_*.dat`), which draw at two screen pixels per map pixel and fill the box. Ask Rusty
  whether 8 is enough; 16 is the same one-line change.
