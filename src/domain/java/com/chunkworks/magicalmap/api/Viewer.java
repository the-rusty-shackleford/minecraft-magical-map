/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.api;

import java.util.Objects;
import java.util.UUID;

/** AF: current requester's identity and permission snapshot. RI: immutable, validated dimension. */
public record Viewer(UUID player, String dimension, boolean operator) {
    public Viewer {
        Objects.requireNonNull(player);
        Location.identifier(dimension);
    }
}
