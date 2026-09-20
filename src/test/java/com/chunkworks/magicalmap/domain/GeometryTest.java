/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Partitions: scale 0/4/outside range; negative/positive coordinates; edges inclusive/exclusive;
 * adjacent sheets; each compass quadrant and wrap; coincident/distant positions. JDK only.
 */
class GeometryTest {
    @Test
    void allScalesUseHalfOpenBoundsAndNegativeCoordinates() {
        for (int scale = 0; scale <= 4; scale++) {
            var sheet = new Sheet(0, "minecraft:overworld", -64, -64, scale, false);
            assertEquals(0, sheet.pixel(sheet.left(), sheet.top()));
            assertEquals(
                    16383,
                    sheet.pixel(
                            sheet.left() + 128 * sheet.step() - .01,
                            sheet.top() + 128 * sheet.step() - .01));
            assertEquals(-1, sheet.pixel(sheet.left() - .01, sheet.top()));
            assertEquals(-1, sheet.pixel(sheet.left() + 128 * sheet.step(), sheet.top()));
            assertEquals(129, sheet.pixel(sheet.left() + sheet.step(), sheet.top() + sheet.step()));
        }
    }

    @Test
    void adjacentSheetsMeetWithoutOverlapOrGap() {
        for (int scale = 0; scale <= 4; scale++) {
            var west = new Sheet(1, "minecraft:overworld", 0, 0, scale, false);
            var east = new Sheet(2, "minecraft:overworld", 128 * (1 << scale), 0, scale, true);
            double boundary = east.left();
            assertTrue(west.contains(boundary - .01, 0));
            assertFalse(west.contains(boundary, 0));
            assertTrue(east.contains(boundary, 0));
        }
    }

    @Test
    void invalidGeometryRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Sheet(-1, "minecraft:overworld", 0, 0, 0, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Sheet(0, "minecraft:overworld", 0, 0, 5, false));
        assertThrows(
                IllegalArgumentException.class, () -> new Sheet(0, "overworld", 0, 0, 0, false));
    }

    @Test
    void bearingsMatchMinecraftYawAndWrap() {
        assertEquals("S", Navigation.heading(Navigation.bearing(0, 0, 0, 10)));
        assertEquals("W", Navigation.heading(Navigation.bearing(0, 0, -10, 0)));
        assertEquals("N", Navigation.heading(Navigation.bearing(0, 0, 0, -10)));
        assertEquals("E", Navigation.heading(Navigation.bearing(0, 0, 10, 0)));
        assertEquals("NW", Navigation.heading(Navigation.bearing(10, 10, 0, 0)));
        assertEquals("S", Navigation.heading(720));
        assertEquals("E", Navigation.heading(-90));
        assertEquals(5, Navigation.distance(-1, -2, 2, 2));
        assertEquals(0, Navigation.distance(2, 2, 2, 2));
    }
}
