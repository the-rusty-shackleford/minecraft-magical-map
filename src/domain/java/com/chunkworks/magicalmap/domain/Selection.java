/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

import java.util.List;

/** Cycling through the locations stacked under one map icon. Nothing may be selected yet, and
 * immutable lists refuse null lookups, so the null case is handled here, once. */
public final class Selection {
    private Selection() {}

    /** requires: ids non-empty; effects: the id after {@code selected} in {@code ids}, wrapping;
     * the first id when nothing or something outside the list is selected; throws:
     * IllegalArgumentException when ids is empty. */
    public static <T> T next(List<T> ids, T selected) {
        if (ids.isEmpty()) throw new IllegalArgumentException("no ids");
        int current = selected == null ? -1 : ids.indexOf(selected);
        return ids.get((current + 1) % ids.size());
    }

    /** requires: ids non-empty; effects: {@code selected} when it is in the list, else the first
     * id; throws: IllegalArgumentException when ids is empty. */
    public static <T> T pick(List<T> ids, T selected) {
        if (ids.isEmpty()) throw new IllegalArgumentException("no ids");
        return selected != null && ids.contains(selected) ? selected : ids.get(0);
    }
}
