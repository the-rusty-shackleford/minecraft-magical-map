/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

import java.util.*;

/**
 * Which sheets of an atlas chart a cell an earlier sheet already charts (D-0006). AF: a plan is
 * the list of (kept, spare) pairs, in atlas order, such that the spare's cell equals the kept's
 * and the kept sheet is the first of that cell. RI: immutable; no id is a spare twice; a kept id
 * is never a spare. JDK only.
 */
public final class Folding {
    private Folding() {}

    /** One spare sheet whose charted pixels belong on the kept sheet of the same cell. */
    public record Fold(int kept, int spare) {
        public Fold {
            if (kept < 0 || spare < 0 || kept == spare)
                throw new IllegalArgumentException("Invalid fold");
        }
    }

    /**
     * requires: sheets in atlas order with distinct ids; effects: returns, in atlas order, a fold
     * for every sheet whose cell an earlier sheet charts, onto the first sheet of that cell;
     * empty when every cell is charted once; throws: none.
     */
    public static List<Fold> plan(List<Sheet> sheets) {
        var first = new HashMap<Sheet.Cell, Integer>();
        var folds = new ArrayList<Fold>();
        for (var sheet : sheets) {
            var kept = first.putIfAbsent(sheet.cell(), sheet.id());
            if (kept != null) folds.add(new Fold(kept, sheet.id()));
        }
        return List.copyOf(folds);
    }

    /** requires: a plan; effects: returns its spare ids in plan order, no repeats; throws: none. */
    public static Set<Integer> spares(List<Fold> folds) {
        var spares = new LinkedHashSet<Integer>();
        for (var fold : folds) spares.add(fold.spare());
        return Collections.unmodifiableSet(spares);
    }
}
