/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import com.chunkworks.magicalmap.api.Location;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

import java.util.Optional;

/** Explicit teleport's bounded arrival search. Never generates terrain or edits the destination. */
public final class SafeArrival {
    private SafeArrival() {}

    /**
     * requires: operator-authorized action; effects: makes the destination chunk available when
     * it is in memory or fully generated on disk, pinning it briefly for the arrival as the
     * vanilla teleport does; returns whether it is. Never generates terrain: a chunk absent from
     * disk, or saved before it was fully generated, refuses arrival. throws: world storage failure.
     */
    public static boolean loadDestination(ServerLevel level, Location location, int ticket) {
        var pos = new ChunkPos((int) Math.floor(location.x()) >> 4, (int) Math.floor(location.z()) >> 4);
        if (level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) != null) return true;
        if (!fullOnDisk(level, pos)) return false;
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, pos, 1, ticket);
        return level.getChunk(pos.x, pos.z, ChunkStatus.FULL, true) != null;
    }

    /**
     * effects: whether the chunk is saved on disk at full generation status, read without loading
     * it; throws: world storage failure.
     */
    public static boolean fullOnDisk(ServerLevel level, ChunkPos pos) {
        var tag = level.getChunkSource().chunkMap.read(pos).join();
        return tag.isPresent() && "minecraft:full".equals(tag.get().getString("Status"));
    }

    /**
     * requires: loaded destination; effects: searches a bounded area of loaded chunks for safe
     * feet; throws: none. A known altitude searches nearby floors; a village uses the local
     * surface.
     */
    public static Optional<Vec3> find(ServerLevel level, ServerPlayer player, Location location) {
        int x = (int) Math.floor(location.x()), z = (int) Math.floor(location.z());
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var column = new BlockPos(x + dx, 0, z + dz);
                    if (!level.hasChunkAt(column)) continue;
                    int y =
                            location.knownHeight()
                                    ? (int) Math.floor(location.y())
                                    : level.getHeight(
                                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                            x + dx,
                                            z + dz);
                    for (int dy : new int[] {0, 1, -1, 2, -2}) {
                        var feet = new BlockPos(x + dx, y + dy, z + dz);
                        if (safe(level, player, feet))
                            return Optional.of(Vec3.atBottomCenterOf(feet));
                    }
                }
        }
        return Optional.empty();
    }

    private static boolean safe(ServerLevel level, ServerPlayer player, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet)
                || level.isOutsideBuildHeight(feet.above())
                || !level.getWorldBorder().isWithinBounds(feet)) return false;
        var floor = level.getBlockState(feet.below());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP)) return false;
        for (int dy = -1; dy <= 1; dy++) {
            var state = level.getBlockState(feet.above(dy));
            if (!state.getFluidState().isEmpty() || dangerous(state.getBlock())) return false;
        }
        var box =
                player.getBoundingBox()
                        .move(Vec3.atBottomCenterOf(feet).subtract(player.position()));
        return level.noCollision(player, box) && !level.containsAnyLiquid(box);
    }

    private static boolean dangerous(Block block) {
        return block instanceof CampfireBlock
                || block instanceof FireBlock
                || block instanceof MagmaBlock
                || block instanceof CactusBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof WitherRoseBlock
                || block instanceof PowderSnowBlock;
    }
}
