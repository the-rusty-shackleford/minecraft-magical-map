# Release verification — 0.2.4

2026-09-24. Village Deed 2.0.0 (Chunkworks) replaced nfx's 1.0.0 in pack 1.61.0; the server's
first start logged "Atlas integration unavailable: Village Deed" with a `ClassNotFoundException`
for `com.nfx.villagedeed.village.VillageClaims`, the class the reflective bridge bound to. No
village had been bought, so no atlas lost a marker, but none could gain one. The bridge now
binds to `com.chunkworks.villagedeed.Claims`, its `Claim` record (`id`, `name`, `centre`, `deed`)
and `Deed.owner`, and the marker stands at the centre the deed records.

- `./gradlew test`: 18 JUnit tests, unchanged.
- `./gradlew runGameTestServer` with `villagedeed-2.0.0.jar`, Thief 1.2.4 and C.A.M.P. 0.2.0 in
  `devtools/integration/`: 19 real-server GameTests, `villageClaimsAreReadFromActualUpstreamLedger`
  now creating a real 2.0.0 claim (`VillageId("structure", <chunk>)`, `Deed.of(owner)`, a centre,
  a price of 45) and revoking it by `VillageId`; the marker's x is the claim's centre plus a half
  block and its owner is exposed; a non-operator cannot resolve the teleport; revocation
  invalidates the stale marker.
- `runPhotoBooth` on an iconified Xephyr, muted, beside Rusty's client, with Sodium, Iris and
  Complementary Unbound 5.8.1, Village Deed 2.0.0 loaded: COMPLETE, 33 checks. The village
  fixture's real claim lists as "Eastwatch Village, 132 blocks E, 136, 8, Owned village" with the
  operator teleport offered ([atlas](0.2.4/04-village.png)); the teleport packet reaches a safe
  village destination ([arrival](0.2.4/08-arrival.png)); no "Atlas integration unavailable" line.
- `./gradlew clean build -PskipBooth` after the booth, on the same tree: JUnit and the 19 GameTests
  green again; jar `magicalmap-0.2.4.jar` sha1 `fbcae17d2fd95dd2f942c8b312ab135f6dddb603`
  (115959 bytes), nothing of Village Deed, Thief or C.A.M.P. inside.
- Not verified: an atlas on the live server showing a village somebody actually bought (none has
  been bought yet); Rusty's first purchase is the first live marker.
