/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

import com.chunkworks.magicalmap.api.Location;

/** AF: geometry of one real 128-square vanilla map. RI: immutable; ID nonnegative; scale 0..4. */
public record Sheet(int id, String dimension, int centerX, int centerZ, int scale, boolean locked) {
    public Sheet {
        Location.identifier(dimension);
        if (id < 0 || scale < 0 || scale > 4) throw new IllegalArgumentException("Invalid sheet");
    }

    /** requires: none; effects: returns blocks per map pixel; throws: none. */
    public int step() {
        return 1 << scale;
    }

    /** requires: none; effects: returns western boundary; throws: none. */
    public int left() {
        return centerX - 64 * step();
    }

    /** requires: none; effects: returns northern boundary; throws: none. */
    public int top() {
        return centerZ - 64 * step();
    }

    /** requires: finite coordinates; effects: tests half-open bounds; throws: none. */
    public boolean contains(double x, double z) {
        return x >= left() && z >= top() && x < left() + 128 * step() && z < top() + 128 * step();
    }

    /** requires: none; effects: returns row-major pixel or -1 outside this sheet; throws: none. */
    public int pixel(double x, double z) {
        return contains(x, z)
                ? (int) ((x - left()) / step()) + 128 * (int) ((z - top()) / step())
                : -1;
    }
}
