# Location providers — protocol 1

Terrain and locations are independent. A marker never reveals map pixels. The atlas
reads real vanilla map data; a provider describes a family of places.

## Values and invariants

`Location` is immutable and JDK-only. Its abstraction function is a visible place
with stable identity, current coordinates and presentation. Its representation
invariant is checked on construction:

- `(provider, id)` is its global identity. IDs must survive movement and cannot be
  reused for a different live place. Providers, kinds, dimensions and item icons
  use validated namespaced identifiers.
- Names have 1–64 characters; local IDs 1–160. Display control codes are rejected.
- Coordinates are finite and within Minecraft's coordinate envelope. `knownHeight`
  distinguishes a surveyed elevation from a surface-only anchor. Unknown Y is not
  displayed or used as an exact arrival height.
- Color is a 24-bit RGB value. Owner is an optional player UUID, not an authorization
  grant. `teleportable` advertises an available action, not a client capability.
- Status is an optional bounded display string, such as `Deployed` or `Unloaded camp`.

`Viewer` contains the requesting player's UUID, current dimension and current
operator status. Provider code must not infer a private viewer from the client.

## Register once per server

Subscribe to `RegisterLocationProvidersEvent` on **NeoForge.EVENT_BUS**, not the mod
bus. It fires after built-ins register, at server startup. Construct your adapter
with `event.server()` and register it with `event.register(provider)`. Registration
rejects duplicate provider IDs and closes when dispatch completes. Do not retain
the event or register a client-only class on a dedicated server.

No separate runtime library is required: the provider API is in Magical Map's jar.
Depend on it at compile time and declare an appropriate NeoForge dependency for an
add-on that requires it. A standalone mod can put its registration subscriber in an
optional compatibility module that loads only when Magical Map is present.

## Implement the port

```java
public interface LocationProvider {
    String id();
    List<Location> snapshot(Viewer viewer);
    Optional<Location> resolve(Viewer viewer, String localId);
}
```

`snapshot` returns an immutable list of at most 256 visible places. It is called on
the server thread, currently every five ticks for a held atlas. Read an existing
index or cached state. Never scan world blocks, force-load chunks, generate terrain
or mutate gameplay state. Filter private locations before returning them. Disconnected
players, revoked claims and packed camps disappear from subsequent snapshots.

`resolve` rechecks identity, existence, visibility and current availability. Return
empty for a removed, hidden, transforming or non-teleportable place. Return its
current `Location` for an authorized destination. Do not cache a previous result
as proof that a subsequent action is permitted. Both methods must agree about
visibility and identity. Navigation-only providers always return empty from `resolve`.

The host independently checks the operator's current permission level, checks the
returned provider and local ID, loads an existing destination chunk, and resolves
again. Movement, removal or loss of availability refuses that request. The host
then searches for safe solid ground near a known height or the surface. Arrival
does not modify terrain, summon vehicles, grant immunity or consume travel items.

Provider failures are isolated and logged once per provider/server session. Its
markers disappear until it recovers, so a failed refresh cannot preserve a stale
action indefinitely. All sources share a bound of 768 visible places per client.
Providers should use stable ordering when presenting bounded subsets.

## Transport and cost

The server assigns a session to the held atlas and its immutable container contents.
Clients receive sheet metadata once, then bounded 16 KiB terrain images only when
their pixels change. Initial images arrive progressively, at most eight per pass;
later passes inspect at most two sheets. Up to one nearby, unlocked sheet receives
a vanilla exploration update per tick. Distant sheets are only read from map data.

Location updates carry changed values and explicit removals, at most four passes
per second. Clients interpolate position changes, snapping across dimensions or
large teleports. Coordinates are presentation data; teleport requests carry only
provider/local ID. Personal landmark requests are separately validated against the
actual player or pixels in their current atlas.

No persistence format for third-party state is imposed. Your mod remains responsible
for its own saves, stable identity, lifecycle and migrations. Personal landmarks
are owned by Magical Map; vanilla map pixels remain owned by Minecraft.

## Your provider on the Azimuth bar

When Azimuth is installed, Magical Map relays every registered provider but its own players'
to Azimuth's bar (`integration/azimuth/AtlasBearings`, provider `magicalmap:atlas`) for viewers
who carry an atlas, within Azimuth's range and in the viewer's dimension only. Your `snapshot`
is what is relayed, so its visibility rules hold on the bar; your `icon` is drawn as the item's
sprite over a dot in your `color`. The same isolation applies: a snapshot that throws or breaks
the contract drops your places from the bar and the atlas together, logged once, and the other
providers stay. A provider that should not appear on the bar has no switch yet; ask. A mod that
wants places on the bar without an atlas implements Azimuth's protocol directly instead.

## Required contract partitions

Test present/moved/removed places; owner/stranger/operator visibility; available and
unavailable actions; loaded and unloaded chunks; different dimensions; duplicate IDs;
snapshots retained across mutation; permission revocation between display and action.
Use real backend integration tests in addition to JDK domain tests. A real client
gate should cover its icon and interaction in the atlas. Never claim integration
coverage merely because an optional dependency was absent and a test skipped it.
