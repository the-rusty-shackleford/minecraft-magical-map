/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.integration;

import com.chunkworks.magicalmap.api.*;

import net.minecraft.server.MinecraftServer;

import java.util.*;

/** Live connected players; skin UUIDs are rendered with the normal client's skin lookup. */
public final class PlayerLocations implements LocationProvider {
    private final MinecraftServer server;

    public PlayerLocations(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String id() {
        return "magicalmap:players";
    }

    @Override
    public List<Location> snapshot(Viewer viewer) {
        return server.getPlayerList().getPlayers().stream()
                .limit(256)
                .map(
                        p ->
                                new Location(
                                        id(),
                                        p.getUUID().toString(),
                                        "magicalmap:player",
                                        p.getGameProfile().getName(),
                                        p.level().dimension().location().toString(),
                                        p.getX(),
                                        p.getY(),
                                        p.getZ(),
                                        true,
                                        "minecraft:player_head",
                                        0xf3dfa1,
                                        Optional.of(p.getUUID()),
                                        false,
                                        ""))
                .toList();
    }

    @Override
    public Optional<Location> resolve(Viewer viewer, String id) {
        return Optional.empty();
    }
}
