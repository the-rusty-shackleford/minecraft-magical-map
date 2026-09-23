/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

import java.util.List;

/**
 * The cartography-table operations the atlas adds, as data a recipe viewer can draw. The table's
 * behaviour lives in the menu mixins; this is the one description both the viewer and the README
 * derive from.
 *
 * <p>AF: {@code ALL} lists every operation; each names its two table inputs and its outputs by
 * item id ("namespace:path"). RI: ids are unique; every operation has exactly two inputs and one or
 * two outputs; every operation involves the atlas as an input or an output. Immutable.
 */
public final class AtlasRecipes {
    /** One table operation: {@code first} and {@code second} are the table's two slots. */
    public record Operation(String id, String first, String second, List<String> outputs) {
        public Operation {
            if (id.isEmpty() || first.isEmpty() || second.isEmpty()) throw new IllegalArgumentException("empty");
            if (outputs.isEmpty() || outputs.size() > 2) throw new IllegalArgumentException("one or two outputs");
            outputs = List.copyOf(outputs);
        }
    }
    public static final String ATLAS = "magicalmap:atlas", MAP = "minecraft:filled_map", BOOK = "minecraft:book",
            SHEARS = "minecraft:shears";
    /** requires: nothing; the three operations in the order the README lists them. */
    public static final List<Operation> ALL = List.of(
            new Operation("magicalmap:cartography/bind_first", MAP, BOOK, List.of(ATLAS)),
            new Operation("magicalmap:cartography/bind_more", ATLAS, MAP, List.of(ATLAS)),
            new Operation("magicalmap:cartography/extract", ATLAS, SHEARS, List.of(ATLAS, MAP)));
    private AtlasRecipes() {}
}
