# Magical Map

Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21. New mod, `magicalmap`, in
`minecraft-magical-map`. Version 0.1.0 is implemented and locally verified;
publication, pack updates and deployment remain unauthorized.

Rusty selected an atlas built from real vanilla map sheets, revealed through
exploration with a meaningful cartography-table workflow. See
[D-0001](decisions/D-0001.md). Live players, owned villages, the viewer's deployed
C.A.M.P. and personal landmarks share a location-provider contract.

On 2026-09-20, six JDK-only JUnit tests and twelve real-server GameTests passed.
The muted client booth passed with actual Village Deed 1.0.0, Thief 1.2.4 and
C.A.M.P. 0.2.0 jars, Sodium/Iris and Complementary Unbound 5.8.1. Rendered atlas,
travel view, player heads, village actions and landmark editor were inspected.
See [verification evidence](../devtools/verification/0.1.0.md) for the scope,
screenshots, commands and limits.

Review checkpoint: local implementation and automated gates are complete. Rusty's
hands-on assessment of the navigation and visual design is still outstanding.
There is no live-server or assembled-pack verification and no release authorization.
The optional integration bridges use validated reflection until their source mods
provide a published API. The public provider protocol is documented in
[location-providers.md](../docs/location-providers.md).
