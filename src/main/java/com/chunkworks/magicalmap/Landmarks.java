/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import com.chunkworks.magicalmap.api.*;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Private world-saved landmarks. AF: owner UUID -> ordered named locations. RI: max 128 per owner;
 * immutable values; only server-thread mutation; every mutation marks persistence dirty.
 */
public final class Landmarks extends SavedData implements LocationProvider {
    public static final String ID = "magicalmap:landmarks";
    public static final int LIMIT = 128;
    private final Map<UUID, LinkedHashMap<String, Location>> players = new HashMap<>();

    /** requires: running server; effects: opens saved landmark store; throws: storage errors. */
    public static Landmarks get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new Factory<>(Landmarks::new, Landmarks::load), "magicalmap_landmarks");
    }

    /**
     * requires: valid persisted tag; effects: loads valid bounded entries, logs damaged entries;
     * throws: none.
     */
    public static Landmarks load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new Landmarks();
        for (var element : tag.getList("Landmarks", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            try {
                UUID owner = entry.getUUID("Owner");
                var location =
                        new Location(
                                ID,
                                entry.getString("Id"),
                                "magicalmap:landmark",
                                entry.getString("Name"),
                                entry.getString("Dimension"),
                                entry.getDouble("X"),
                                entry.getDouble("Y"),
                                entry.getDouble("Z"),
                                entry.getBoolean("Height"),
                                entry.getString("Icon"),
                                entry.getInt("Color"),
                                Optional.of(owner),
                                true,
                                "");
                var entries =
                        result.players.computeIfAbsent(owner, unused -> new LinkedHashMap<>());
                if (entries.size() < LIMIT) entries.put(location.id(), location);
            } catch (RuntimeException damaged) {
                LoggerFactory.getLogger(Landmarks.class)
                        .warn("Ignoring invalid saved atlas landmark", damaged);
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        players.forEach(
                (owner, locations) ->
                        locations
                                .values()
                                .forEach(
                                        location -> {
                                            var entry = new CompoundTag();
                                            entry.putUUID("Owner", owner);
                                            entry.putString("Id", location.id());
                                            entry.putString("Name", location.name());
                                            entry.putString("Dimension", location.dimension());
                                            entry.putDouble("X", location.x());
                                            entry.putDouble("Y", location.y());
                                            entry.putDouble("Z", location.z());
                                            entry.putBoolean("Height", location.knownHeight());
                                            entry.putString("Icon", location.icon());
                                            entry.putInt("Color", location.color());
                                            entries.add(entry);
                                        }));
        tag.put("Landmarks", entries);
        return tag;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Location> snapshot(Viewer viewer) {
        var entries = players.get(viewer.player());
        return entries == null ? List.of() : List.copyOf(entries.values());
    }

    /** effects: the viewer's own landmark with this id, since 0.2.2 a teleport target. */
    @Override
    public Optional<Location> resolve(Viewer viewer, String id) {
        var entries = players.get(viewer.player());
        return entries == null ? Optional.empty() : Optional.ofNullable(entries.get(id));
    }

    /**
     * requires: validated personal landmark; effects: adds/updates owned entry, persists it;
     * throws: wrong owner/provider or full store.
     */
    public void put(UUID owner, Location location) {
        if (!location.provider().equals(ID) || !location.owner().equals(Optional.of(owner)))
            throw new IllegalArgumentException("Wrong owner");
        var entries = players.computeIfAbsent(owner, unused -> new LinkedHashMap<>());
        if (entries.size() >= LIMIT && !entries.containsKey(location.id()))
            throw new IllegalArgumentException("Landmark limit reached (128)");
        entries.put(location.id(), teleportable(location));
        setDirty();
    }

    /** effects: the same landmark marked as an operator teleport target. */
    private static Location teleportable(Location l) {
        return l.teleportable() ? l : new Location(l.provider(), l.id(), l.kind(), l.name(), l.dimension(), l.x(), l.y(), l.z(),
                l.knownHeight(), l.icon(), l.color(), l.owner(), true, l.status());
    }

    /** requires: owner and local id; effects: removes only this player's entry; throws: none. */
    public void remove(UUID owner, String id) {
        var entries = players.get(owner);
        if (entries != null && entries.remove(id) != null) {
            if (entries.isEmpty()) players.remove(owner);
            setDirty();
        }
    }
}
