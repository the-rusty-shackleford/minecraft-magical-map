/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: nothing selected (the crash of 0.1.0: an immutable list asked for null); selected
 * outside the list; selected first / middle / last (wrap); one-element list; empty list rejected;
 * both immutable and mutable lists. */
final class SelectionTest {
    private static final List<String> IDS = List.of("a", "b", "c");
    @Test void nothingSelectedStartsAtTheFirst() {
        assertEquals("a", Selection.next(IDS, null));
        assertEquals("a", Selection.pick(IDS, null));
        assertEquals("a", Selection.next(new java.util.ArrayList<>(IDS), null));
    }
    @Test void unknownSelectionStartsAtTheFirst() {
        assertEquals("a", Selection.next(IDS, "zz"));
        assertEquals("a", Selection.pick(IDS, "zz"));
    }
    @Test void knownSelectionAdvancesAndWraps() {
        assertEquals("b", Selection.next(IDS, "a"));
        assertEquals("c", Selection.next(IDS, "b"));
        assertEquals("a", Selection.next(IDS, "c"));
        assertEquals("b", Selection.pick(IDS, "b"));
        assertEquals("only", Selection.next(List.of("only"), "only"));
    }
    @Test void emptyListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Selection.next(List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> Selection.pick(List.of(), "a"));
    }
}
