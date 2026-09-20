/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.gametest;

import com.chunkworks.magicalmap.api.*;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;

/** Actual test-mod extension installed through the same public event as third-party providers. */
@EventBusSubscriber(modid = "magicalmap_gametest")
public final class TestDestinations {
    public static final Map<String, Location> PLACES = new HashMap<>();

    @SubscribeEvent
    public static void register(RegisterLocationProvidersEvent event) {
        PLACES.clear();
        event.register(
                new LocationProvider() {
                    public String id() {
                        return "atlas_test:destinations";
                    }

                    public List<Location> snapshot(Viewer viewer) {
                        return List.copyOf(PLACES.values());
                    }

                    public Optional<Location> resolve(Viewer viewer, String id) {
                        return viewer.operator()
                                ? Optional.ofNullable(PLACES.get(id))
                                : Optional.empty();
                    }
                });
    }
}
