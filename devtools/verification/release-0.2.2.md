# Release verification — 0.2.2

2026-09-22. Rusty's screenshot: on their ultrawide at GUI scale 5 (258 rows) the location
list drew over "Choose a destination" (D-0003).

- `./gradlew test`: 12 JUnit tests.
- `./gradlew runGameTestServer`: 17 real-server GameTests. Own landmarks are operator teleport
  targets and a stranger's landmark does not resolve (D-0005). Three more in `ArrivalGameTests`:
  a loaded destination chunk is available; a chunk generated 50,000 blocks away and saved is
  found on disk at full status and loaded for the arrival; a never-generated chunk is refused
  and stays ungenerated (Rusty's "Destination terrain is unavailable" at their own village,
  D-0004). The booth's village teleport phase, run before this change, covers the loaded case.
- `devtools/booth.sh` flow run through `runPhotoBooth` on an iconified Xephyr display,
  muted, beside Rusty's own client: COMPLETE, twenty-four phases. Three new phases switch to
  GUI scale 3 at 720p, the 240-row worst case, and assert the stacked sidebar, list mode
  with nothing selected, a selection, and that "Back to list" deselects.
- Inspected: [list mode](0.2.2/11-atlas-short-list.png) (two rows, the page counter in its
  own space, the bottom hint dropping whole segments that do not fit),
  [details](0.2.2/12-atlas-short-details.png) (Back to list, name, distance, coordinates,
  status, Track and Teleport all above the bottom bar) and
  [after Back](0.2.2/13-atlas-short-back.png). The tall layout at the booth's usual 360
  rows is unchanged, as its earlier photos show.
- The booth's cartography step now waits for the screen instead of a fixed 15 ticks; it
  had flaked twice under CPU contention.
- `./gradlew build -PskipBooth`: green; jar `magicalmap-0.2.2.jar` sha1 `bc877cbb02e8ddddc5db816d61a0c922c9ec290c`. Built
  and committed; release waits on Rusty's go.
