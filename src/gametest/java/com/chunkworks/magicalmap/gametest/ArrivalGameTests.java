/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.gametest;

import com.chunkworks.magicalmap.SafeArrival;
import com.chunkworks.magicalmap.api.Location;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Optional;

/** Partitions for the destination chunk: in memory (available); on disk at full status but not in
 * memory (available, loaded without generating); never generated (refused, and still not
 * generated afterwards). The far chunks sit 50,000 blocks from the test so nothing else loads
 * them. */
@GameTestHolder("magicalmap")
@PrefixGameTestTemplate(false)
public final class ArrivalGameTests {
    private static Location at(GameTestHelper h, double x, double z) {
        return new Location("atlas_test:destinations", "far-" + (long) x + "-" + (long) z, "atlas_test:survey", "Far post",
                h.getLevel().dimension().location().toString(), x, 64, z, false, "minecraft:beacon", 0xabcdef, Optional.empty(), true, "");
    }
    @GameTest(template = "arena")
    public void loadedDestinationIsAvailable(GameTestHelper h) {
        var here = h.absolutePos(new net.minecraft.core.BlockPos(5, 2, 5));
        h.assertTrue(SafeArrival.loadDestination(h.getLevel(), at(h, here.getX() + .5, here.getZ() + .5), 1), "a loaded chunk is available");
        h.succeed();
    }
    @GameTest(template = "arena")
    public void ungeneratedDestinationIsRefusedAndStaysUngenerated(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new net.minecraft.core.BlockPos(0, 0, 0));
        double x = origin.getX() + 50_000 + 8, z = origin.getZ() + 50_000 + 8;
        var pos = new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        h.assertTrue(!SafeArrival.fullOnDisk(level, pos), "far chunk is not on disk");
        h.assertTrue(!SafeArrival.loadDestination(level, at(h, x, z), 2), "far chunk is refused");
        h.assertTrue(level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) == null, "refusal generated nothing");
        h.succeed();
    }
    @GameTest(template = "arena", timeoutTicks = 400)
    public void savedDestinationIsReadFromDiskWithoutBeingLoaded(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new net.minecraft.core.BlockPos(0, 0, 0));
        double x = origin.getX() - 50_000 + 8, z = origin.getZ() - 50_000 + 8;
        var pos = new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        h.assertTrue(level.getChunk(pos.x, pos.z, ChunkStatus.FULL, true) != null, "chunk generated for the test");
        level.getChunkSource().save(true);
        h.assertTrue(SafeArrival.fullOnDisk(level, pos), "generated chunk is on disk at full status");
        h.assertTrue(SafeArrival.loadDestination(level, at(h, x, z), 3), "a saved chunk is available");
        h.assertTrue(level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) != null, "and loaded for the arrival");
        h.succeed();
    }
}
