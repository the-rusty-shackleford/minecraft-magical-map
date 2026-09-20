/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.chunkworks.magicalmap.api.*;

import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Partitions: owner/stranger/operator; present/moved/removed; finite/nonfinite positions;
 * valid/invalid namespaced identifiers; immutable snapshots; new kinds without renderer changes. A
 * complete in-memory provider implements the port, not a call-sequence mock.
 */
class ProviderContractTest {
    private static final UUID OWNER = UUID.randomUUID();

    private static Location place(double x) {
        return new Location(
                "example:beacons",
                "home",
                "example:beacon",
                "Home beacon",
                "minecraft:overworld",
                x,
                70,
                0,
                true,
                "minecraft:beacon",
                0xabcdef,
                Optional.of(OWNER),
                true,
                "");
    }

    private static final class Beacons implements LocationProvider {
        private Location location = place(0);

        public String id() {
            return "example:beacons";
        }

        public List<Location> snapshot(Viewer viewer) {
            return location != null && viewer.player().equals(OWNER)
                    ? List.of(location)
                    : List.of();
        }

        public Optional<Location> resolve(Viewer viewer, String id) {
            return viewer.operator()
                    ? snapshot(viewer).stream().filter(p -> p.id().equals(id)).findFirst()
                    : Optional.empty();
        }
    }

    @Test
    void privateProviderRevalidatesMovementRemovalAndPermission() {
        var provider = new Beacons();
        var owner = new Viewer(OWNER, "minecraft:overworld", true);
        var snapshot = provider.snapshot(owner);
        var key = snapshot.getFirst().key();
        assertTrue(
                provider.snapshot(new Viewer(UUID.randomUUID(), "minecraft:overworld", true))
                        .isEmpty());
        assertTrue(
                provider.resolve(new Viewer(OWNER, "minecraft:overworld", false), "home")
                        .isEmpty());
        provider.location = place(300);
        assertEquals(key, provider.resolve(owner, "home").orElseThrow().key());
        assertEquals(300, provider.resolve(owner, "home").orElseThrow().x());
        assertEquals(0, snapshot.getFirst().x());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        provider.location = null;
        assertTrue(provider.resolve(owner, "home").isEmpty());
    }

    @Test
    void valueBoundaryRejectsHostileAndInvalidInput() {
        for (double x : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 30_000_001})
            assertThrows(IllegalArgumentException.class, () -> place(x));
        assertThrows(IllegalArgumentException.class, () -> Location.text("\u00a7kHidden", 64));
        assertThrows(IllegalArgumentException.class, () -> Location.text("a\nb", 64));
        assertThrows(IllegalArgumentException.class, () -> Location.text(" ", 64));
        assertThrows(IllegalArgumentException.class, () -> Location.identifier("MissingNamespace"));
        assertNotEquals(new Location.Key("a:b", "c:d"), new Location.Key("a:b/c", "d"));
    }
}
