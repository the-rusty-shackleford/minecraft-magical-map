# Release verification — 0.2.1

2026-09-22. Rusty's client crashed on clicking a map icon (crash report
`crash-2026-09-22_20.37.40-client.txt`: `AtlasScreen.mouseClicked` line 500,
`ImmutableCollections.List12.indexOf(null)`). Nothing was selected, so the icon's immutable
id list was asked for the position of null.

- Fix: `Selection.next` and `Selection.pick` in the domain handle the null and absent cases;
  `AtlasScreen` and `AtlasCanvas` call them.
- `./gradlew clean test`: 12 JUnit tests, four new in `SelectionTest` (nothing selected,
  unknown selection, advance and wrap, one-entry stack, empty list rejected).
- `./gradlew runGameTestServer`: 13 real-server GameTests.
- `devtools/booth.sh`: COMPLETE, twenty-one phases, on the first run.
- `./gradlew build -PskipBooth`: jar `magicalmap-0.2.1.jar`
  sha1 `7ab03ff0bbfcc039206c975f1bea574977793c55`, matched on the GitHub asset. Folded into
  pack 1.55.0 at 01:28:54 UTC on 2026-09-23, before that pack's first server restart.
