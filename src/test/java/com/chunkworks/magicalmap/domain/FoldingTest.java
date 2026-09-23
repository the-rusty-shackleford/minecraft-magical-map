/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.chunkworks.magicalmap.domain.Folding.Fold;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Partitions: no sheets / one / all cells distinct; a cell charted twice and thrice, in and out
 * of id order; same centre at another scale or in another dimension; locked against unlocked;
 * spares of an empty and a repeated plan; invalid folds; immutability. JDK only.
 */
class FoldingTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private static Sheet at(int id, int x, int z, int scale) {
        return new Sheet(id, OVERWORLD, x, z, scale, false);
    }

    @Test
    void nothingFoldsWhileEveryCellIsChartedOnce() {
        assertEquals(List.of(), Folding.plan(List.of()));
        assertEquals(List.of(), Folding.plan(List.of(at(1, 0, 0, 0))));
        assertEquals(
                List.of(),
                Folding.plan(
                        List.of(
                                at(1, 0, 0, 0),
                                at(2, 128, 0, 0),
                                at(3, 0, 0, 1),
                                new Sheet(4, "minecraft:the_nether", 0, 0, 0, false))));
        assertEquals(List.of(), Folding.spares(List.of()).stream().toList());
    }

    @Test
    void laterSheetsOfACellFoldOntoItsFirstInAtlasOrder() {
        var folds =
                Folding.plan(
                        List.of(at(5, 0, 0, 0), at(2, 128, 0, 0), at(9, 0, 0, 0), at(1, 0, 0, 0)));
        assertEquals(List.of(new Fold(5, 9), new Fold(5, 1)), folds);
        assertEquals(List.of(9, 1), Folding.spares(folds).stream().toList());
    }

    @Test
    void twoCellsFoldIndependently() {
        var folds =
                Folding.plan(
                        List.of(at(1, 0, 0, 0), at(2, 128, 0, 0), at(3, 128, 0, 0), at(4, 0, 0, 0)));
        assertEquals(List.of(new Fold(2, 3), new Fold(1, 4)), folds);
    }

    @Test
    void lockingDoesNotSeparateACell() {
        var folds =
                Folding.plan(
                        List.of(
                                new Sheet(1, OVERWORLD, 0, 0, 0, true),
                                new Sheet(2, OVERWORLD, 0, 0, 0, false)));
        assertEquals(List.of(new Fold(1, 2)), folds);
    }

    @Test
    void cellCarriesDimensionScaleAndCentreOnly() {
        assertEquals(at(1, 64, -64, 2).cell(), new Sheet(7, OVERWORLD, 64, -64, 2, true).cell());
        assertNotEquals(at(1, 64, -64, 2).cell(), at(1, 64, -64, 1).cell());
        assertNotEquals(at(1, 64, -64, 2).cell(), at(1, 64, 64, 2).cell());
    }

    @Test
    void foldsAndPlansRejectInvalidInputAndMutation() {
        assertThrows(IllegalArgumentException.class, () -> new Fold(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Fold(-1, 2));
        var plan = Folding.plan(List.of(at(1, 0, 0, 0), at(2, 0, 0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> plan.add(new Fold(1, 3)));
        assertThrows(UnsupportedOperationException.class, () -> Folding.spares(plan).add(4));
    }
}
