/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.integration.azimuth;

import com.chunkworks.azimuth.api.AzimuthLocation;
import com.chunkworks.azimuth.api.AzimuthProvider;
import com.chunkworks.azimuth.api.AzimuthViewer;
import com.chunkworks.magicalmap.AtlasServer;
import com.chunkworks.magicalmap.MagicalMap;
import com.chunkworks.magicalmap.api.Location;
import com.chunkworks.magicalmap.api.LocationProvider;
import com.chunkworks.magicalmap.api.Viewer;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The atlas as an Azimuth provider: while the viewer carries an atlas, every place the atlas
 * would list for them except the players (Azimuth shows those itself) goes on their bar. Each
 * location provider's own visibility rules apply, since their snapshots are taken for this
 * viewer; the key keeps the provider's name inside the id, so a village stays "villagedeed:
 * villages/…" under "magicalmap:atlas".
 */
public final class AtlasBearings implements AzimuthProvider {
    public static final String ID = "magicalmap:atlas";
    static final String PLAYERS = "magicalmap:players";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<AzimuthLocation> bearings(AzimuthViewer viewer, double range) {
        var server = AtlasServer.server();
        if (server == null) return List.of();
        var player = server.getPlayerList().getPlayer(viewer.player());
        if (player == null || !carriesAtlas(player)) return List.of();
        return bearings(viewer, range, AtlasServer.providers(), player.hasPermissions(2));
    }

    /**
     * requires: the providers registered for this server; effects: the places within range in
     * the viewer's dimension from every provider but the players', in provider order; a provider
     * that fails is dropped on its own, logged once, exactly as the atlas drops it.
     */
    static List<AzimuthLocation> bearings(
            AzimuthViewer viewer, double range, Map<String, LocationProvider> providers, boolean operator) {
        var who = new Viewer(viewer.player(), viewer.dimension(), operator);
        var out = new ArrayList<AzimuthLocation>();
        for (var provider : providers.values()) {
            if (provider.id().equals(PLAYERS)) continue;
            for (var place : AtlasServer.places(provider, who)) {
                if (!place.dimension().equals(viewer.dimension())) continue;
                double dx = place.x() - viewer.x(), dz = place.z() - viewer.z();
                if (dx * dx + dz * dz > range * range) continue;
                out.add(bearing(place));
            }
        }
        return List.copyOf(out);
    }

    /** effects: the atlas's place as a bearing under this provider's name. */
    static AzimuthLocation bearing(Location place) {
        return new AzimuthLocation(
                ID,
                place.provider() + "/" + place.id(),
                place.name(),
                place.dimension(),
                place.x(),
                place.y(),
                place.z(),
                place.icon(),
                place.color());
    }

    static boolean carriesAtlas(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (inventory.getItem(slot).is(MagicalMap.ATLAS.get())) return true;
        return false;
    }
}
