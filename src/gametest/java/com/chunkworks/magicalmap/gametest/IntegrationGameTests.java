/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.gametest;

import com.chunkworks.magicalmap.*;
import com.chunkworks.magicalmap.api.*;
import com.chunkworks.magicalmap.integration.OptionalLocations;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

/**
 * Partitions: real optional mods absent/present; claim/revocation; deployed/folding/packed camp;
 * own/other player; real operator/nonoperator; safe/unsafe/removed destination. Upstream jars are
 * optional test runtime dependencies. Absence is explicitly reported, never counted as integration
 * coverage.
 */
@GameTestHolder("magicalmap")
@PrefixGameTestTemplate(false)
public final class IntegrationGameTests {
    @GameTest(template = "arena")
    public void villageClaimsAreReadFromActualUpstreamLedger(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("villagedeed")) {
            h.succeed();
            System.out.println("INTEGRATION NOT RUN: Village Deed absent");
            return;
        }
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        long id = new ChunkPos(pos).toLong();
        claim(level, id, "Eastwatch Village", UUID.randomUUID());
        var registration = new RegisterLocationProvidersEvent(level.getServer());
        OptionalLocations.register(registration);
        var provider = registration.finish().get("villagedeed:villages");
        h.assertTrue(provider != null, "actual Village Deed integration registered");
        var viewer = new Viewer(UUID.randomUUID(), level.dimension().location().toString(), true);
        var marker =
                provider.snapshot(viewer).stream()
                        .filter(p -> p.name().equals("Eastwatch Village"))
                        .findFirst()
                        .orElseThrow();
        h.assertTrue(
                marker.x() == new ChunkPos(pos).getMiddleBlockX() && marker.owner().isPresent(),
                "real claim geometry and buyer exposed");
        h.assertTrue(
                provider.resolve(
                                new Viewer(viewer.player(), viewer.dimension(), false), marker.id())
                        .isEmpty(),
                "nonoperator cannot resolve teleport");
        var claims = Class.forName("com.nfx.villagedeed.village.VillageClaims");
        claims.getMethod("revoke", long.class)
                .invoke(claims.getMethod("get", ServerLevel.class).invoke(null, level), id);
        h.assertTrue(
                provider.resolve(viewer, marker.id()).isEmpty(),
                "revocation invalidates stale marker");
        h.succeed();
    }

    /** Creates a real Village Deed record; shared by server tests and the client booth. */
    public static void claim(ServerLevel level, long id, String name, UUID owner) throws Exception {
        var claims = Class.forName("com.nfx.villagedeed.village.VillageClaims");
        var claim = Class.forName("com.nfx.villagedeed.village.VillageClaims$Claim");
        var value =
                claim.getConstructor(long.class, String.class, UUID.class, String.class, long.class)
                        .newInstance(id, name, owner, "Cartographer", level.getGameTime());
        claims.getMethod("claim", claim)
                .invoke(claims.getMethod("get", ServerLevel.class).invoke(null, level), value);
    }

    @GameTest(template = "arena", timeoutTicks = 440)
    public void campMarkerFollowsRealDeploymentAndPacking(GameTestHelper h) {
        if (!ModList.get().isLoaded("mobilecamp")) {
            h.succeed();
            System.out.println("INTEGRATION NOT RUN: C.A.M.P. absent");
            return;
        }
        for (int x = 0; x < 32; x++)
            for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var player = h.makeMockServerPlayerInLevel();
        player.setNoGravity(true);
        net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(
                        player.connection.getConnection())
                .add(Payloads.Notice.TYPE.id());
        var anchor = h.absolutePos(new BlockPos(10, 2, 10));
        player.moveTo(Vec3.atBottomCenterOf(anchor.north(2)));
        player.setYRot(0);
        var item =
                new ItemStack(
                        BuiltInRegistries.ITEM.get(ResourceLocation.parse("mobilecamp:camp")));
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var placement =
                item.useOn(
                        new UseOnContext(
                                player,
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(
                                        Vec3.atCenterOf(anchor.below()).add(0, .5, 0),
                                        Direction.UP,
                                        anchor.below(),
                                        false)));
        h.assertTrue(placement.consumesAction(), "actual C.A.M.P. placed");
        var registration = new RegisterLocationProvidersEvent(h.getLevel().getServer());
        OptionalLocations.register(registration);
        var provider = registration.finish().get("mobilecamp:camps");
        var viewer =
                new Viewer(player.getUUID(), h.getLevel().dimension().location().toString(), true);
        h.assertTrue(
                !provider.snapshot(viewer).getFirst().teleportable(),
                "deploying camp has no arrival action");
        h.assertTrue(
                provider.snapshot(new Viewer(UUID.randomUUID(), viewer.dimension(), true))
                        .isEmpty(),
                "other player's camp remains private even to operator");
        h.runAtTickTime(
                185,
                () -> {
                    var marker = provider.snapshot(viewer).getFirst();
                    h.assertTrue(marker.teleportable(), "active camp permits operator arrival");
                    h.assertTrue(
                            marker.x() == anchor.getX() + .5 && marker.y() == anchor.getY(),
                            "actual owner ledger anchor");
                    player.server
                            .getPlayerList()
                            .getOps()
                            .add(
                                    new net.minecraft.server.players.ServerOpListEntry(
                                            player.getGameProfile(), 2, false));
                    AtlasServer.teleport(player, marker.provider(), marker.id());
                    h.assertTrue(
                            player.position().distanceTo(Vec3.atBottomCenterOf(anchor)) < 12,
                            "operator arrives at the actual deployed camp");
                    player.moveTo(Vec3.atBottomCenterOf(anchor.north(2)));
                    h.useBlock(new BlockPos(10, 2, 10), player);
                    h.assertTrue(
                            !provider.snapshot(viewer).getFirst().teleportable(),
                            "folding invalidates action immediately");
                });
        h.runAtTickTime(
                380,
                () -> {
                    h.assertTrue(
                            provider.snapshot(viewer).isEmpty(), "packed camp removed from map");
                    player.server.getPlayerList().deop(player.getGameProfile());
                    player.server.getPlayerList().remove(player);
                    h.succeed();
                });
    }

    @GameTest(template = "arena")
    public void realOperatorGateAndSafeArrivalRejectRemovedOrHazardousDestinations(
            GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var server = h.getLevel().getServer();
        var profile = player.getGameProfile();
        // Embedded GameTest connections skip login negotiation. Declare only the response
        // channel needed by this server-side gate; the real client booth tests negotiation.
        net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(
                        player.connection.getConnection())
                .add(Payloads.Notice.TYPE.id());
        var anchor = h.absolutePos(new BlockPos(10, 2, 10));
        for (int x = 0; x < 32; x++)
            for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var location =
                new Location(
                        "atlas_test:destinations",
                        "safe",
                        "atlas_test:survey",
                        "Survey post",
                        h.getLevel().dimension().location().toString(),
                        anchor.getX() + .5,
                        anchor.getY(),
                        anchor.getZ() + .5,
                        true,
                        "minecraft:beacon",
                        0xabcdef,
                        Optional.empty(),
                        true,
                        "");
        TestDestinations.PLACES.put("safe", location);
        server.getPlayerList().deop(profile);
        boolean denied = false;
        try {
            AtlasServer.teleport(player, location.provider(), location.id());
        } catch (IllegalArgumentException expected) {
            denied = true;
        }
        h.assertTrue(denied, "current nonoperator permission refuses teleport");
        // GameTestServer.getOperatorUserPermissionLevel() is 0, so the vanilla op command
        // cannot establish a level-2 operator in this harness. Use the actual operator list.
        server.getPlayerList()
                .getOps()
                .add(new net.minecraft.server.players.ServerOpListEntry(profile, 2, false));
        h.assertTrue(player.hasPermissions(2), "real operator list grants permission");
        AtlasServer.teleport(player, location.provider(), location.id());
        h.assertTrue(
                player.position().distanceTo(Vec3.atBottomCenterOf(anchor)) < 2,
                "actual server player arrives safely");
        TestDestinations.PLACES.remove("safe");
        denied = false;
        try {
            AtlasServer.teleport(player, location.provider(), location.id());
        } catch (IllegalArgumentException expected) {
            denied = true;
        }
        h.assertTrue(denied, "removed destination cannot be used from stale UI");
        for (int x = 0; x < 32; x++)
            for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.MAGMA_BLOCK);
        h.assertTrue(
                SafeArrival.find(h.getLevel(), player, location).isEmpty(),
                "magma floor refuses arrival");
        server.getPlayerList().deop(profile);
        server.getPlayerList().remove(player);
        h.succeed();
    }
}
