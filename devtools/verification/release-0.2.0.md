# Release verification — 0.2.0

2026-09-22. Rusty requested the creative tab and the EMI recipe and authorized the
release in the same message.

- `./gradlew test`: 8 JUnit tests (Geometry, ProviderContract, AtlasRecipes).
- `./gradlew runGameTestServer`: 13 real-server GameTests, including the creative
  tab rebuild (Magical Map tab lists the atlas alone; Tools & Utilities still lists it).
- `devtools/booth.sh` on Xephyr with llvmpipe, actual Village Deed 1.0.0, Thief 1.2.4,
  C.A.M.P. 0.2.0, Sodium/Iris and EMI 1.1.24+1.21.1 jars: COMPLETE, twenty-one phases.
  The first run failed at the pre-existing cartography step ("real cartography screen
  opens") when launched straight after another booth; the rerun passed. EMI logged no
  "not present in recipe manager" error once synthetic ids carried the leading slash.
- Inspected: [creative tab](0.2.0/09-creative-tab.png) shows the Magical Map tab with
  the atlas; [EMI](0.2.0/10-emi-cartography.png) shows the Cartography Table category
  with map + book, atlas + map, and atlas + shears giving atlas + map.
- `./gradlew clean build -PskipBooth`: green; jar `magicalmap-0.2.0.jar`
  sha1 `216e2f538ba502677c3e2135da7a78ea5d60cace`.
