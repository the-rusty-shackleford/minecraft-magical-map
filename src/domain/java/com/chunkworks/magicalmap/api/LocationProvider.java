/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.api;

import java.util.List;
import java.util.Optional;

/**
 * Server-side port for a family of places. Called synchronously on the server thread. Snapshot
 * queries must be bounded, must not load/generate chunks, and must omit private places. Stable IDs
 * survive movement; removed places disappear. Implementations retain ownership of their state and
 * return immutable snapshots. No client or renderer types cross this boundary.
 */
public interface LocationProvider {
    /** requires: none; effects: returns stable namespaced provider ID; throws: none. */
    String id();

    /**
     * requires: current viewer; effects: returns up to 256 visible places, without world mutation;
     * throws: operational errors (host isolates and logs provider failures).
     */
    List<Location> snapshot(Viewer viewer);

    /**
     * requires: current viewer and local ID; effects: revalidates identity, visibility and current
     * destination. Empty means removed, inaccessible or not teleportable. No chunk loads. throws:
     * operational errors. Host separately checks operator permission and safe arrival.
     */
    Optional<Location> resolve(Viewer viewer, String id);
}
