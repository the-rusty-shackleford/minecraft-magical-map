/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.integration.azimuth;

import com.chunkworks.azimuth.api.AzimuthProviders;

/**
 * Registers the atlas with Azimuth. Kept apart from the mod's entry so nothing of Azimuth is
 * loaded unless the entry finds Azimuth present.
 */
public final class AzimuthBridge {
    private AzimuthBridge() {}

    /** requires: Azimuth is loaded; effects: the atlas becomes an Azimuth provider. */
    public static void register() {
        AzimuthProviders.register(new AtlasBearings());
    }
}
