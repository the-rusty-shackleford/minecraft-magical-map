# Release verification — 0.2.2

2026-09-22. Rusty's screenshot: on their ultrawide at GUI scale 5 (258 rows) the location
list drew over "Choose a destination" (D-0003).

- `./gradlew test`: 12 JUnit tests.
- `./gradlew runGameTestServer`: 13 real-server GameTests.
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
- `./gradlew build -PskipBooth`: green; jar `magicalmap-0.2.2.jar` sha1 `9778cfd8486ea71d8c741963829ca0db4ae6b800`. Built
  and committed; release waits on Rusty's go.
