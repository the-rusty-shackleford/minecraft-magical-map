/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable visible place. AF: a provider-owned identity, position and presentation. RI: bounded
 * nonempty names, namespaced kind/icon, finite coordinates within the world; optional owner
 * identifies a player, never grants permissions. Unknown height is explicit.
 */
public record Location(
        String provider,
        String id,
        String kind,
        String name,
        String dimension,
        double x,
        double y,
        double z,
        boolean knownHeight,
        String icon,
        int color,
        Optional<UUID> owner,
        boolean teleportable,
        String status) {
    public Location {
        provider = identifier(provider);
        id = text(id, 160);
        kind = identifier(kind);
        name = text(name, 64);
        dimension = identifier(dimension);
        icon = identifier(icon);
        status = Objects.requireNonNull(status);
        if (status.length() > 80) throw new IllegalArgumentException("Status too long");
        owner = Objects.requireNonNull(owner);
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000
                || Math.abs(z) > 30_000_000
                || Math.abs(y) > 30_000_000) throw new IllegalArgumentException("Invalid position");
        if ((color & 0xff000000) != 0) throw new IllegalArgumentException("Color must be RGB");
    }

    /** requires: nonnull identifier; effects: validates namespacing; throws: invalid identifier. */
    public static String identifier(String value) {
        if (value == null || value.length() > 160 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Expected namespaced identifier");
        return value;
    }

    /** requires: text; effects: validates a bounded display value; throws: invalid text. */
    public static String text(String value, int limit) {
        if (value == null
                || value.isBlank()
                || value.length() > limit
                || value.chars().anyMatch(c -> Character.isISOControl(c) || c == '\u00a7'))
            throw new IllegalArgumentException("Invalid text");
        return value;
    }

    /** requires: none; effects: returns globally unique identity; throws: none. */
    public Key key() {
        return new Key(provider, id);
    }

    /** AF: provider-local identity; RI: nonnull validated components, immutable. */
    public record Key(String provider, String id) {
        public Key {
            identifier(provider);
            text(id, 160);
        }
    }
}
