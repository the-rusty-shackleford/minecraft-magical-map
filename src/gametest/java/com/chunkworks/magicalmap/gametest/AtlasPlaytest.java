/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.gametest;

import com.chunkworks.magicalmap.*;
import com.chunkworks.magicalmap.api.Location;
import com.chunkworks.magicalmap.client.AtlasClient;
import com.mojang.blaze3d.platform.GlUtil;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Manual client fixture. AF: a disposable atlas course and a simulated surveying companion. RI:
 * setup runs once, exclusively in the playtest launch; all world writes are on the server thread,
 * all UI work on the client thread. After readiness, no input or screen is controlled. The
 * companion uses a real server-player entry but no second rendering client or human session. Its
 * embedded transport is drained and acknowledges keepalives for bounded long-session storage.
 */
@EventBusSubscriber(modid = "magicalmap_gametest", value = Dist.CLIENT)
public final class AtlasPlaytest {
    private static final Logger LOG = LoggerFactory.getLogger("Atlas playtest");
    private static int age;
    private static boolean scheduled, handedOver;
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static ServerPlayer peer;
    private static int peerTicks;
    private static int observedTicks;

    /** requires: client tick; effects: initializes and verifies the course once; throws: none. */
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("magicalmap.playtest")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (handedOver) {
            if (++observedTicks == 400)
                LOG.info(
                        "atlas playtest: renderer {} at {} FPS", GlUtil.getRenderer(), mc.getFps());
            return;
        }
        try {
            if (failure != null) throw new IllegalStateException("Course setup failed", failure);
            if (++age > 2400) throw new IllegalStateException("Course setup timed out");
            if (!scheduled && age > 20) {
                String renderer = GlUtil.getRenderer().toLowerCase(java.util.Locale.ROOT);
                if (renderer.contains("llvmpipe")
                        || renderer.contains("softpipe")
                        || renderer.contains("software"))
                    throw new IllegalStateException(
                            "Interactive playtest requires GPU rendering; detected "
                                    + GlUtil.getRenderer());
                scheduled = true;
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(
                        () -> {
                            try {
                                prepare(server.getPlayerList().getPlayer(id));
                                prepared = true;
                            } catch (Throwable error) {
                                failure = error;
                            }
                        });
            }
            if (!prepared) return;
            if (Boolean.getBoolean("magicalmap.playtest.resume")) {
                if (age < 60) return;
                handedOver = true;
                LOG.info(
                        "atlas playtest: READY - resumed saved course using {}",
                        GlUtil.getRenderer());
                mc.gui
                        .getChat()
                        .addMessage(
                                Component.literal(
                                        "Practice world resumed. M: atlas | N: travel map."));
                return;
            }
            if (AtlasClient.loadedSheetCount() != 2 || age < 220) return;
            if (AtlasClient.locations().stream()
                            .filter(p -> p.kind().equals("magicalmap:player"))
                            .count()
                    != 2) throw new IllegalStateException("Both player markers must be present");
            if (AtlasClient.locations().stream()
                    .noneMatch(p -> p.name().equals("Eastwatch Village") && p.teleportable()))
                throw new IllegalStateException("Village destination must be available");
            if (AtlasClient.locations().stream()
                    .noneMatch(p -> p.provider().equals("mobilecamp:camps") && p.teleportable()))
                throw new IllegalStateException("Deployed camp must be available");
            if (!mc.player.getInventory().getItem(0).has(DataComponents.WRITTEN_BOOK_CONTENT))
                throw new IllegalStateException("Field guide must be in the first hotbar slot");
            mc.gui
                    .getChat()
                    .addMessage(
                            Component.literal(
                                    "Atlas playtest ready. M: atlas | N: travel map. Read the field"
                                        + " guide in hotbar slot 1. You have operator access."));
            mc.gui
                    .getChat()
                    .addMessage(
                            Component.literal(
                                    "Walk east across the bridge to reveal blank terrain. Surveyor"
                                            + " is a moving test companion. Quit whenever you are"
                                            + " finished."));
            Screenshot.grab(
                    mc.gameDirectory,
                    "playtest-ready.png",
                    mc.getMainRenderTarget(),
                    message -> LOG.info("{}", message.getString()));
            handedOver = true;
            LOG.info(
                    "atlas playtest: READY - two sheets, two heads, active camp, village and field"
                            + " guide; controls handed to player");
        } catch (Throwable error) {
            LOG.error("atlas playtest: FAIL", error);
            handedOver = true;
            mc.stop();
        }
    }

    /** requires: server tick; effects: advances only the simulated companion; throws: none. */
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("magicalmap.playtest") || peer == null || !prepared) return;
        if (peer.server != event.getServer() || peer.hasDisconnected()) return;
        peerTicks++;
        double angle = peerTicks * .012;
        peer.moveTo(
                12 + 16 * Math.cos(angle),
                64,
                -28 + 10 * Math.sin(angle),
                (float) Math.toDegrees(angle),
                0);
        if (peer.connection.getConnection().channel() instanceof EmbeddedChannel channel) {
            channel.runPendingTasks();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                if (message instanceof ClientboundKeepAlivePacket keepalive)
                    peer.connection.handleKeepAlive(
                            new ServerboundKeepAlivePacket(keepalive.getId()));
                ReferenceCountUtil.release(message);
            }
        }
        if (peerTicks == 800)
            LOG.info("atlas playtest: companion remains connected after 40 seconds");
    }

    private static void prepare(ServerPlayer player) {
        if (Boolean.getBoolean("magicalmap.playtest.resume")) {
            peer = AtlasBooth.createPeer(player);
            return;
        }
        peer = AtlasBooth.prepare(player);
        var level = player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, player.server);
        var first = player.getInventory().getItem(9).copy();
        var second = player.getInventory().getItem(11).copy();
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.OFF_HAND, AtlasPages.create(List.of(first, second)));
        player.getInventory().setItem(0, guide());
        player.getInventory().setItem(1, new ItemStack(Items.IRON_PICKAXE));
        player.getInventory().setItem(2, new ItemStack(Items.TORCH, 32));
        player.getInventory().setItem(3, new ItemStack(Items.BREAD, 32));
        player.getInventory().setItem(9, first.copy());
        player.getInventory().setItem(10, new ItemStack(Items.BOOK, 8));
        player.getInventory().setItem(11, second.copy());
        player.getInventory().setItem(12, new ItemStack(Items.SHEARS));
        player.getInventory().setItem(13, new ItemStack(Items.PAPER, 32));
        player.getInventory().setItem(14, new ItemStack(Items.MAP, 8));
        player.getInventory().setItem(15, new ItemStack(Items.GLASS_PANE, 8));
        player.getInventory().selected = 0;
        // A bridge on the route and two small village buildings around a clear arrival plaza.
        for (int x = 30; x <= 62; x++)
            for (int z = 12; z <= 18; z++)
                level.setBlock(new BlockPos(x, 63, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        hut(player, 124, -8, Blocks.OAK_PLANKS);
        hut(player, 141, -8, Blocks.OAK_PLANKS);
        level.setBlock(new BlockPos(136, 64, 3), Blocks.BELL.defaultBlockState(), 3);
        hut(player, -29, 35, Blocks.STONE);
        level.setBlock(new BlockPos(-26, 65, 39), Blocks.IRON_ORE.defaultBlockState(), 3);
        var dimension = level.dimension().location().toString();
        Landmarks.get(player.server)
                .put(
                        player.getUUID(),
                        new Location(
                                Landmarks.ID,
                                "iron-mine",
                                "magicalmap:landmark",
                                "Mine entrance",
                                dimension,
                                -26,
                                64,
                                34,
                                true,
                                "minecraft:iron_pickaxe",
                                0xe8bd64,
                                Optional.of(player.getUUID()),
                                false,
                                ""));
        Landmarks.get(player.server)
                .put(
                        player.getUUID(),
                        new Location(
                                Landmarks.ID,
                                "river-ford",
                                "magicalmap:landmark",
                                "River bridge",
                                dimension,
                                44,
                                64,
                                15,
                                true,
                                "minecraft:oak_boat",
                                0x75bec6,
                                Optional.of(player.getUUID()),
                                false,
                                ""));
        if (!player.hasPermissions(2))
            throw new IllegalStateException("Playtest needs operator access");
        if (AtlasPages.charted(player.getOffhandItem(), level, dimension, 180, 0))
            throw new IllegalStateException("Eastern frontier must start uncharted");
        player.teleportTo(level, 4.5, 64, -8.5, -65, 10);
        player.containerMenu.broadcastChanges();
        LOG.info("atlas playtest: course prepared with an uncharted eastern frontier");
    }

    private static void hut(ServerPlayer player, int x, int z, Block material) {
        var level = player.serverLevel();
        for (int dx = 0; dx <= 6; dx++)
            for (int dz = 0; dz <= 5; dz++)
                for (int y = 64; y <= 68; y++) {
                    boolean wall = dx == 0 || dx == 6 || dz == 0 || dz == 5;
                    var block = y == 68 || wall ? material : Blocks.AIR;
                    if (dz == 0 && dx == 3 && y < 67) block = Blocks.AIR;
                    level.setBlock(new BlockPos(x + dx, y, z + dz), block.defaultBlockState(), 3);
                }
    }

    private static ItemStack guide() {
        List<String> pages =
                List.of(
                        "MAGICAL ATLAS\n\n"
                                + "Your atlas is in your offhand.\n\n"
                                + "M: open atlas\n"
                                + "N: toggle travel map\n"
                                + "WASD + mouse: explore\n\n"
                                + "Creative mode and operator access are enabled.",
                        "1. EXPLORE\n\n"
                            + "Walk east across the bridge. Blank terrain fills as you travel.\n\n"
                            + "Open M. Drag to pan; scroll to zoom. Recenter returns to you.\n\n"
                            + "Surveyor is a moving test companion.",
                        "2. FIND YOUR WAY\n\n"
                            + "Select Mine entrance, then Track destination. Close the atlas and"
                            + " follow the direction and distance.\n\n"
                            + "At the mine, use Mark current position to save your own landmark.",
                        "3. LANDMARKS\n\n"
                            + "Choose a name, icon and color. Save, then select your marker to edit"
                            + " or delete it.\n\n"
                            + "Right-click a charted map point to mark it. Blank points are"
                            + " refused.",
                        "4. DESTINATIONS\n\n"
                            + "Select Eastwatch Village and Teleport (operator).\n\n"
                            + "Try the same with your C.A.M.P.\n\n"
                            + "You can pack and redeploy the camp to check that its marker follows"
                            + " it.",
                        "5. CARTOGRAPHY\n\n"
                                + "Table: 6, 64, -7\n\n"
                                + "Map + book: new atlas\n"
                                + "Atlas + map: bind\n"
                                + "Atlas + shears: recover\n\n"
                                + "Spare supplies are in your inventory. Duplicate map IDs are"
                                + " refused.",
                        "6. WRAP UP\n\n"
                            + "Try a copied or locked sheet and toggle N. Move the atlas out of"
                            + " both hands: the travel view disappears.\n\n"
                            + "Lost? /tp @s 4 64 -8\n\n"
                            + "Quit when done. Relaunch to resume; --reset starts a fresh course.");
        var book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(
                DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(
                        Filterable.passThrough("Atlas field guide"),
                        "Cartographer",
                        0,
                        pages.stream()
                                .map(
                                        text ->
                                                Filterable.<Component>passThrough(
                                                        Component.literal(text)))
                                .toList(),
                        true));
        return book;
    }
}
