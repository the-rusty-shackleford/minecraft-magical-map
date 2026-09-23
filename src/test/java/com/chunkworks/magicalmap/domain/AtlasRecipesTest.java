/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the shipped list (three operations, unique ids, atlas on every operation, the
 * extraction alone returns two items, order matches the README); malformed operations (empty
 * field, no outputs, three outputs); immutability. */
final class AtlasRecipesTest {
    @Test void shippedOperationsMatchTheTable() {
        var all = AtlasRecipes.ALL;
        assertEquals(3, all.size());
        var ids = new HashSet<String>();
        for (var op : all) {
            assertTrue(ids.add(op.id()), op.id());
            assertTrue(op.first().equals(AtlasRecipes.ATLAS) || op.outputs().contains(AtlasRecipes.ATLAS), op.id());
        }
        assertEquals(List.of(AtlasRecipes.ATLAS), all.get(0).outputs());
        assertEquals(AtlasRecipes.BOOK, all.get(0).second());
        assertEquals(List.of(AtlasRecipes.ATLAS, AtlasRecipes.MAP), all.get(2).outputs());
        assertEquals(AtlasRecipes.SHEARS, all.get(2).second());
        assertThrows(UnsupportedOperationException.class, () -> all.get(0).outputs().clear());
    }
    @Test void rejectsMalformedOperations() {
        assertThrows(IllegalArgumentException.class, () -> new AtlasRecipes.Operation("", "a:b", "c:d", List.of("e:f")));
        assertThrows(IllegalArgumentException.class, () -> new AtlasRecipes.Operation("x:y", "a:b", "c:d", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AtlasRecipes.Operation("x:y", "a:b", "c:d", List.of("1:1", "2:2", "3:3")));
    }
}
