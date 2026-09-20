/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.gametest;

import com.chunkworks.magicalmap.*;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.*;

/**
 * Partitions: atlas capacity 64/65; overworld/nether geometry; codec valid/oversized images; actual
 * packet handler with current/stale session, charted/unmapped position, equipped/unequipped.
 * Embedded transport uses actual payload codecs and server packet handlers; client negotiation
 * remains the real-client booth's responsibility.
 */
@GameTestHolder("magicalmap")
@PrefixGameTestTemplate(false)
public final class ProtocolGameTests {
    @GameTest(template = "arena")
    public void capacityAndDimensionsRetainOriginalItems(GameTestHelper h) {
        var maps = new ArrayList<ItemStack>();
        for (int i = 0; i < 64; i++)
            maps.add(MapItem.create(h.getLevel(), i * 128, 0, (byte) (i % 5), true, false));
        var atlas = AtlasPages.create(maps);
        var extra = MapItem.create(h.getLevel(), 9000, 0, (byte) 0, true, false);
        h.assertTrue(
                AtlasCartography.result(atlas, extra).isEmpty()
                        && AtlasPages.maps(atlas).size() == 64
                        && !extra.isEmpty(),
                "capacity refusal preserves all 65 maps");
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        var netherMap = MapItem.create(nether, 0, 0, (byte) 0, true, false);
        var mixed = AtlasPages.create(List.of(maps.getFirst(), netherMap));
        var sheets = AtlasPages.sheets(mixed, h.getLevel());
        h.assertTrue(
                sheets.size() == 2 && !sheets.get(0).dimension().equals(sheets.get(1).dimension()),
                "same coordinates in different dimensions remain different sheets");
        h.succeed();
    }

    @GameTest(template = "arena")
    public void terrainCodecIsBoundedAndDefensivelyCopied(GameTestHelper h) {
        byte[] colors = new byte[16384];
        colors[123] = 42;
        var terrain = new Payloads.Terrain(7, 9, colors);
        colors[123] = 1;
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            Payloads.Terrain.CODEC.encode(buffer, terrain);
            var decoded = Payloads.Terrain.CODEC.decode(buffer);
            h.assertTrue(
                    decoded.session() == 7 && decoded.mapId() == 9 && decoded.colors()[123] == 42,
                    "real codec roundtrip has no mutable aliases");
            byte[] leaked = decoded.colors();
            leaked[123] = 2;
            h.assertTrue(decoded.colors()[123] == 42, "getter cannot mutate payload");
            buffer.clear();
            buffer.writeLong(7);
            buffer.writeVarInt(9);
            buffer.writeByteArray(new byte[16385]);
            boolean rejected = false;
            try {
                Payloads.Terrain.CODEC.decode(buffer);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            h.assertTrue(rejected, "oversized image rejected before use");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 80)
    public void actualHandlerRejectsStaleSessionUnmappedPointAndUnequippedAtlas(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        p.setNoGravity(true);
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        p.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        var channels = ChannelAttributes.getOrCreateAdHocChannels(p.connection.getConnection());
        channels.addAll(
                List.of(
                        Payloads.State.TYPE.id(),
                        Payloads.Terrain.TYPE.id(),
                        Payloads.Notice.TYPE.id(),
                        Payloads.Action.TYPE.id()));
        var negotiated =
                new HashMap<
                        net.minecraft.resources.ResourceLocation,
                        net.neoforged.neoforge.network.registration.NetworkChannel>();
        for (var id : channels)
            negotiated.put(
                    id, new net.neoforged.neoforge.network.registration.NetworkChannel(id, "1"));
        ChannelAttributes.setPayloadSetup(
                p.connection.getConnection(),
                new net.neoforged.neoforge.network.registration.NetworkPayloadSetup(
                        Map.of(net.minecraft.network.ConnectionProtocol.PLAY, negotiated)));
        var atlas =
                AtlasPages.create(
                        List.of(
                                MapItem.create(
                                        h.getLevel(),
                                        pos.getX(),
                                        pos.getZ(),
                                        (byte) 0,
                                        true,
                                        false)));
        p.setItemInHand(InteractionHand.OFF_HAND, atlas);
        var transport = (EmbeddedChannel) p.connection.getConnection().channel();
        long[] session = {-1};
        h.runAtTickTime(
                8,
                () -> {
                    transport.runPendingTasks();
                    Object packet;
                    while ((packet = transport.readOutbound()) != null)
                        if (packet instanceof ClientboundCustomPayloadPacket custom
                                && custom.payload() instanceof Payloads.State state
                                && state.reset()
                                && !state.sheets().isEmpty()) session[0] = state.session();
                    h.assertTrue(session[0] >= 0, "real server emitted held-atlas session");
                    send(
                            p,
                            new Payloads.Action(
                                    session[0] + 999,
                                    Payloads.Operation.ADD_HERE,
                                    "",
                                    "",
                                    "Forged session",
                                    "minecraft:torch",
                                    0xaabbcc,
                                    "",
                                    0,
                                    0));
                });
        h.runAtTickTime(
                14,
                () ->
                        send(
                                p,
                                new Payloads.Action(
                                        session[0],
                                        Payloads.Operation.ADD_POINT,
                                        "",
                                        "",
                                        "Unmapped point",
                                        "minecraft:torch",
                                        0xaabbcc,
                                        h.getLevel().dimension().location().toString(),
                                        pos.getX() + 1_000_000,
                                        pos.getZ())));
        h.runAtTickTime(
                20,
                () ->
                        send(
                                p,
                                new Payloads.Action(
                                        session[0],
                                        Payloads.Operation.ADD_HERE,
                                        "",
                                        "",
                                        "Valid position",
                                        "minecraft:torch",
                                        0xaabbcc,
                                        "",
                                        0,
                                        0)));
        h.runAtTickTime(
                26,
                () -> {
                    var bookmarks = Landmarks.get(p.server).snapshot(AtlasServer.viewer(p));
                    h.assertTrue(
                            bookmarks.size() == 1
                                    && bookmarks.getFirst().name().equals("Valid position"),
                            "only valid actual handler request persisted");
                    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    send(
                            p,
                            new Payloads.Action(
                                    session[0],
                                    Payloads.Operation.ADD_HERE,
                                    "",
                                    "",
                                    "After unequip",
                                    "minecraft:torch",
                                    0xaabbcc,
                                    "",
                                    0,
                                    0));
                });
        h.runAtTickTime(
                32,
                () -> {
                    var bookmarks = Landmarks.get(p.server).snapshot(AtlasServer.viewer(p));
                    h.assertTrue(
                            bookmarks.size() == 1,
                            "unequipped atlas cannot authorize stale request");
                    Landmarks.get(p.server).remove(p.getUUID(), bookmarks.getFirst().id());
                    p.server.getPlayerList().remove(p);
                    transport.finishAndReleaseAll();
                    h.succeed();
                });
    }

    private static void send(ServerPlayer player, Payloads.Action action) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            Payloads.Action.CODEC.encode(buffer, action);
            player.connection.handleCustomPayload(
                    new ServerboundCustomPayloadPacket(Payloads.Action.CODEC.decode(buffer)));
        } finally {
            buffer.release();
        }
    }
}
