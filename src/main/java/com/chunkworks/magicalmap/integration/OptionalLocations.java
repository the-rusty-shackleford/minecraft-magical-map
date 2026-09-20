/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.integration;

import com.chunkworks.magicalmap.api.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;

/**
 * Narrow versioned bridges to released mods without a published Java API artifact. Reflection is
 * confined here, signatures checked once at server startup; no jar is copied into our mod. New
 * integrations should implement the public provider port instead. No chunk scans or loads.
 */
public final class OptionalLocations {
    private OptionalLocations() {}

    /**
     * requires: registration window; effects: registers supported installed mods, logs incompatible
     * APIs; throws: none.
     */
    public static void register(RegisterLocationProvidersEvent event) {
        if (ModList.get().isLoaded("villagedeed")) register(event, true);
        if (ModList.get().isLoaded("mobilecamp")) register(event, false);
    }

    private static void register(RegisterLocationProvidersEvent event, boolean village) {
        try {
            event.register(village ? new Villages(event.server()) : new Camp(event.server()));
        } catch (ReflectiveOperationException failure) {
            LoggerFactory.getLogger(OptionalLocations.class)
                    .error(
                            "Atlas integration unavailable: "
                                    + (village ? "Village Deed" : "C.A.M.P."),
                            failure);
        }
    }

    private static Object call(Method method, Object receiver, Object... args) {
        try {
            return method.invoke(receiver, args);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Location integration failed: " + method, error);
        }
    }

    private abstract static class Bridge implements LocationProvider {
        final MinecraftServer server;

        Bridge(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public Optional<Location> resolve(Viewer viewer, String id) {
            if (!viewer.operator()) return Optional.empty();
            return snapshot(viewer).stream()
                    .filter(p -> p.id().equals(id) && p.teleportable())
                    .findFirst();
        }
    }

    private static final class Villages extends Bridge {
        private final Method get, all, villageId, name, buyer;

        Villages(MinecraftServer server) throws ReflectiveOperationException {
            super(server);
            var type = Class.forName("com.nfx.villagedeed.village.VillageClaims");
            var claim = Class.forName("com.nfx.villagedeed.village.VillageClaims$Claim");
            get = type.getMethod("get", ServerLevel.class);
            all = type.getMethod("all");
            villageId = claim.getMethod("villageId");
            name = claim.getMethod("villageName");
            buyer = claim.getMethod("buyer");
        }

        @Override
        public String id() {
            return "villagedeed:villages";
        }

        @Override
        public List<Location> snapshot(Viewer viewer) {
            var result = new ArrayList<Location>();
            for (var level : server.getAllLevels()) {
                var claims = (Collection<?>) call(all, call(get, null, level));
                for (var claim : claims) {
                    if (result.size() == 256) break;
                    long id = (Long) call(villageId, claim);
                    var chunk = new ChunkPos(id);
                    var dimension = level.dimension().location().toString();
                    result.add(
                            new Location(
                                    id(),
                                    dimension + "/" + id,
                                    "villagedeed:village",
                                    (String) call(name, claim),
                                    dimension,
                                    chunk.getMiddleBlockX(),
                                    0,
                                    chunk.getMiddleBlockZ(),
                                    false,
                                    "minecraft:bell",
                                    0xe5b85b,
                                    Optional.of((UUID) call(buyer, claim)),
                                    viewer.operator(),
                                    "Owned village"));
                }
            }
            return List.copyOf(result);
        }
    }

    private static final class Camp extends Bridge {
        private final Method get, active, dimension, position, phase;
        private final Class<?> coreType;

        Camp(MinecraftServer server) throws ReflectiveOperationException {
            super(server);
            var type = Class.forName("com.chunkworks.mobilecamp.CampOwners");
            var site = Class.forName("com.chunkworks.mobilecamp.CampOwners$Site");
            coreType = Class.forName("com.chunkworks.mobilecamp.CampBlockEntity");
            get = type.getMethod("get", ServerLevel.class);
            active = type.getMethod("active", UUID.class);
            dimension = site.getMethod("dimension");
            position = site.getMethod("position");
            phase = coreType.getMethod("phase");
        }

        @Override
        public String id() {
            return "mobilecamp:camps";
        }

        @Override
        public List<Location> snapshot(Viewer viewer) {
            var site = call(active, call(get, null, server.overworld()), viewer.player());
            if (site == null) return List.of();
            String dimensionId = (String) call(dimension, site);
            var pos = BlockPos.of((Long) call(position, site));
            var level =
                    server.getLevel(
                            ResourceKey.create(
                                    Registries.DIMENSION, ResourceLocation.parse(dimensionId)));
            String status = "Unloaded camp";
            boolean ready = level != null;
            if (level != null && level.hasChunkAt(pos)) {
                var core = level.getBlockEntity(pos);
                ready = coreType.isInstance(core) && call(phase, core).toString().equals("ACTIVE");
                status = ready ? "Deployed" : "Transforming or unavailable";
            }
            return List.of(
                    new Location(
                            id(),
                            viewer.player().toString(),
                            "mobilecamp:camp",
                            "Your C.A.M.P.",
                            dimensionId,
                            pos.getX() + .5,
                            pos.getY(),
                            pos.getZ() + .5,
                            true,
                            "minecraft:campfire",
                            0x86b68a,
                            Optional.of(viewer.player()),
                            ready && viewer.operator(),
                            status));
        }
    }
}
