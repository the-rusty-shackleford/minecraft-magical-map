# Release verification — 0.2.3

2026-09-23. Rusty's "Another UI clash" screenshot: a selected landmark offered Teleport
(operator), Edit and Delete on one row (D-0003 amendment). Then "my atlas claims there are 9
sheets in it but realistically I only see two": maps of one grid square with different ids
(D-0006), folded into this release on Rusty's word.

- `./gradlew test`: 18 JUnit tests, six new in `FoldingTest` (no folds while every cell is
  charted once; later sheets of a cell fold onto its first in atlas order; two cells fold
  independently; locking does not separate a cell; a cell is dimension, scale and centre
  only; invalid folds and mutation are refused).
- `./gradlew runGameTestServer`: 19 real-server GameTests. Two new: binding a map of a cell
  the atlas already charts leaves the atlas at one sheet, consumes the map, and the spare's
  charted pixels join the kept sheet with the kept pixel winning; an atlas holding four
  sheets of two cells folds when held to the first sheet of each cell, in order, keeping its
  name, with every spare's pixels joined, and folds no further.
- `devtools/booth.sh` flow through `runPhotoBooth` on an iconified Xephyr, muted, beside
  Rusty's client: COMPLETE. The atlas is built from three real maps, the third of the first's
  cell; the server folds it as the atlas is held and the client shows two sheets and the
  notice ([atlas](0.2.3/03-atlas.png)). On the 240-row layout a landmark is selected after
  Back to list and the three buttons are asserted on distinct rows inside the frame
  ([photo](0.2.3/14-atlas-short-landmark-rows.png)); the plain selection is unchanged
  ([details](0.2.3/12-atlas-short-details.png)).
- Runs: four of five green after the last product change. The one failure was phase 2, the
  cartography screen not opening within 6 s while the server logged itself 70 ticks behind
  under back-to-back runs; the booth's cap is now 20 s and the run after it was green.
  Before that, two booth flakes were traced by logging rather than guessed: the screen laid
  itself out only when rendering, so under llvmpipe a click in a tick with no frame since the
  last click tested against stale geometry and showed its widgets only at the next frame.
  Layout now runs before every click and scroll and after every click.
- The first booth run of the fold folded the *second* sheet in the client's table preview:
  client map data carries no centre, so every client-side sheet is cell (0, 0). Folding is
  decided only on a `ServerLevel`; the client predicts a bind and the server's slot update
  corrects it. Both sides were logged to confirm before the log was removed.
- `./gradlew build -PskipBooth`: green; jar `magicalmap-0.2.3.jar` sha1
  `7e05c55501e6d4e6df8276c0ed14187d26e79577`. Release on Rusty's "release everything you just
  built once you're satisfied".
