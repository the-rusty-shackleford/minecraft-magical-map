/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.gametest;

import com.chunkworks.magicalmap.*;
import com.chunkworks.magicalmap.api.*;
import com.chunkworks.magicalmap.client.*;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.slf4j.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Real-client gate: actual table click packets, live map sync, movement keys, marker UI, bookmark
 * packets, teleport and texture/session cleanup. Shader screenshots require inspection.
 */
@EventBusSubscriber(modid = "magicalmap_gametest", value = Dist.CLIENT)
public final class AtlasBooth {
    private static final Logger LOG = LoggerFactory.getLogger("Atlas booth");
    private static final BlockPos TABLE = new BlockPos(6, 64, -7);
    private static final int DONE = 21;
    private static int phase, age, total;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static ServerPlayer peer;
    private static double startX, startZ;
    private static long started = System.currentTimeMillis();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("magicalmap.booth") || phase >= DONE) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        try {
            if (failure != null) throw new IllegalStateException("Server fixture failed", failure);
            if (++total > 2400 || System.currentTimeMillis() - started > 240_000)
                throw new IllegalStateException("Booth deadline exceeded at phase " + phase);
            age++;
            switch (phase) {
                case 0 -> {
                    if (age == 20) {
                        server(mc, AtlasBooth::prepare);
                        next();
                    }
                }
                case 1 -> {
                    if (ready && age > 60) {
                        ready = false;
                        mc.gameMode.useItemOn(
                                mc.player,
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(
                                        Vec3.atCenterOf(TABLE), Direction.NORTH, TABLE, false));
                        next();
                    }
                }
                case 2 -> {
                    if (age > 15) {
                        check(
                                mc.screen instanceof CartographyTableScreen,
                                "real cartography screen opens");
                        click(mc, 3, ClickType.QUICK_MOVE);
                        click(mc, 4, ClickType.QUICK_MOVE);
                        next();
                    }
                }
                case 3 -> {
                    if (age > 15) {
                        check(
                                mc.player
                                        .containerMenu
                                        .getSlot(2)
                                        .getItem()
                                        .is(MagicalMap.ATLAS.get()),
                                "client sees atlas recipe output");
                        photo(mc, "01-cartography");
                        click(mc, 2, ClickType.QUICK_MOVE);
                        next();
                    }
                }
                case 4 -> {
                    if (age == 15)
                        server(
                                mc,
                                p -> {
                                    var atlas = takeAtlas(p);
                                    p.getInventory().setItem(9, atlas);
                                    var second = p.getInventory().getItem(11);
                                    p.getInventory().setItem(11, ItemStack.EMPTY);
                                    p.getInventory().setItem(10, second);
                                    p.containerMenu.broadcastChanges();
                                    ready = true;
                                });
                    if (ready && age > 25) {
                        ready = false;
                        click(mc, 3, ClickType.QUICK_MOVE);
                        click(mc, 4, ClickType.QUICK_MOVE);
                        next();
                    }
                }
                case 5 -> {
                    if (age > 15) {
                        check(
                                AtlasPages.maps(mc.player.containerMenu.getSlot(2).getItem()).size()
                                        == 2,
                                "second sheet bound through client clicks");
                        click(mc, 2, ClickType.QUICK_MOVE);
                        next();
                    }
                }
                case 6 -> {
                    if (age == 15) {
                        mc.player.closeContainer();
                        server(
                                mc,
                                p -> {
                                    var atlas = takeAtlas(p);
                                    check(
                                            AtlasPages.maps(atlas).size() == 2,
                                            "server atlas has both real sheets");
                                    p.setItemInHand(InteractionHand.OFF_HAND, atlas);
                                    ready = true;
                                });
                    }
                    if (ready && age > 40) {
                        ready = false;
                        check(
                                AtlasClient.sheets().size() == 2
                                        && AtlasClient.loadedSheetCount() == 2,
                                "real map textures synchronized");
                        check(
                                AtlasClient.locations().stream()
                                                .filter(p -> p.kind().equals("magicalmap:player"))
                                                .count()
                                        == 2,
                                "two real server players have head markers");
                        startX = mc.player.getX();
                        startZ = mc.player.getZ();
                        mc.player.setYRot(0);
                        mc.options.keyUp.setDown(true);
                        next();
                    }
                }
                case 7 -> {
                    if (age > 25) {
                        mc.options.keyUp.setDown(false);
                        check(
                                Math.hypot(mc.player.getX() - startX, mc.player.getZ() - startZ)
                                        > 2,
                                "player moves with atlas travel view active");
                        mc.player.setYRot(-65);
                        photo(mc, "02-travel");
                        KeyMapping.click(ClientSetup.OPEN.getKey());
                        next();
                    }
                }
                case 8 -> {
                    if (age > 20) {
                        check(
                                mc.screen instanceof AtlasScreen,
                                "registered atlas key opens planning view");
                        photo(mc, "03-atlas");
                        select(mc, "Eastwatch");
                        next();
                    }
                }
                case 9 -> {
                    if (age > 12) {
                        check(
                                AtlasClient.selected() != null
                                        && AtlasClient.selected().teleportable(),
                                "village exposes operator action");
                        photo(mc, "04-village");
                        mc.screen.mouseClicked(
                                mc.getWindow().getGuiScaledWidth() - 90,
                                mc.getWindow().getGuiScaledHeight() - 142 + 73,
                                0);
                        mc.screen.onClose();
                        next();
                    }
                }
                case 10 -> {
                    if (age > 15) {
                        check(
                                AtlasClient.tracked() != null,
                                "track button retains destination in travel view");
                        photo(mc, "05-tracking");
                        KeyMapping.click(ClientSetup.OPEN.getKey());
                        next();
                    }
                }
                case 11 -> {
                    if (age > 15) {
                        mc.screen.mouseClicked(mc.getWindow().getGuiScaledWidth() - 100, 65, 0);
                        check(
                                mc.screen instanceof LandmarkScreen,
                                "mark-here opens landmark editor");
                        for (char c : "Booth waypoint".toCharArray()) mc.screen.charTyped(c, 0);
                        next();
                    }
                }
                case 12 -> {
                    if (age > 8) {
                        photo(mc, "06-landmark-editor");
                        mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                        next();
                    }
                }
                case 13 -> {
                    if (age > 15) {
                        check(
                                AtlasClient.locations().stream()
                                        .anyMatch(p -> p.name().equals("Booth waypoint")),
                                "bookmark saved through actual network request");
                        select(mc, "Booth waypoint");
                        next();
                    }
                }
                case 14 -> {
                    if (age > 12) {
                        photo(mc, "07-landmark");
                        select(mc, "Eastwatch");
                        next();
                    }
                }
                case 15 -> {
                    if (age > 8) {
                        mc.screen.mouseClicked(
                                mc.getWindow().getGuiScaledWidth() - 90,
                                mc.getWindow().getGuiScaledHeight() - 142 + 98,
                                0);
                        next();
                    }
                }
                case 16 -> {
                    if (age == 25) {
                        check(
                                Math.hypot(mc.player.getX() - 136, mc.player.getZ() - 8) < 12,
                                "operator teleport packet reaches a safe village destination");
                        mc.screen.onClose();
                    }
                    if (age > 35) {
                        photo(mc, "08-arrival");
                        server(
                                mc,
                                p -> {
                                    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                                    p.server.getPlayerList().remove(peer);
                                });
                        next();
                    }
                }
                case 17 -> {
                    if (age > 20) {
                        check(
                                AtlasClient.sheets().isEmpty()
                                        && AtlasClient.loadedSheetCount() == 0,
                                "unequipping clears authorized session and closes textures");
                        server(mc, p -> p.setGameMode(GameType.CREATIVE));
                        next();
                    }
                }
                case 18 -> {
                    if (age == 20) {
                        var selected = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
                        selected.setAccessible(true);
                        selected.set(null, MagicalMap.TAB.get());
                        mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), false));
                    }
                    if (age > 30) {
                        check(mc.screen instanceof CreativeModeInventoryScreen
                                && mc.player.containerMenu.getSlot(0).getItem().is(MagicalMap.ATLAS.get()),
                                "Magical Map creative tab opens with the atlas in its first slot");
                        photo(mc, "09-creative-tab");
                        mc.screen.onClose();
                        next();
                    }
                }
                case 19 -> {
                    if (age == 10) EmiApi.displayRecipes(EmiStack.of(MagicalMap.ATLAS.get()));
                    if (age > 25) {
                        check(mc.screen != null && mc.screen.getClass().getName().startsWith("dev.emi"),
                                "EMI opens the atlas's recipe screen");
                        photo(mc, "10-emi-cartography");
                        mc.screen.onClose();
                        next();
                    }
                }
                case 20 -> {
                    if (age > 10) {
                        LOG.info("atlas booth: COMPLETE");
                        mc.stop();
                        phase = DONE;
                    }
                }
                default -> {}
            }
        } catch (Throwable error) {
            LOG.error("atlas booth: FAIL phase " + phase, error);
            mc.options.keyUp.setDown(false);
            mc.stop();
            phase = DONE;
        }
    }

    private static void next() {
        phase++;
        age = 0;
    }

    private static void click(Minecraft mc, int slot, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId, slot, 0, type, mc.player);
    }

    private static void select(Minecraft mc, String name) {
        var rows =
                AtlasClient.locations().stream()
                        .filter(
                                p ->
                                        p.dimension()
                                                .equals(
                                                        mc.player
                                                                .level()
                                                                .dimension()
                                                                .location()
                                                                .toString()))
                        .sorted(Comparator.comparing(Location::name, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        int index = -1;
        for (int i = 0; i < rows.size(); i++)
            if (rows.get(i).name().contains(name)) {
                index = i;
                break;
            }
        check(index >= 0, "location available: " + name);
        int x = mc.getWindow().getGuiScaledWidth() - 150;
        // Reset the list with actual scroll input, then expose the desired row.
        for (int i = 0; i < rows.size(); i++) mc.screen.mouseScrolled(x, 135, 0, 1);
        int visible = Math.max(1, (mc.getWindow().getGuiScaledHeight() - 157 - 128) / 23);
        int offset = Math.max(0, index - visible + 1);
        for (int i = 0; i < offset; i++) mc.screen.mouseScrolled(x, 135, 0, -1);
        mc.screen.mouseClicked(x, 128 + (index - offset) * 23 + 8, 0);
    }

    private static ItemStack takeAtlas(ServerPlayer player) {
        for (int i = 0; i < 36; i++)
            if (player.getInventory().getItem(i).is(MagicalMap.ATLAS.get())) {
                var atlas = player.getInventory().getItem(i);
                player.getInventory().setItem(i, ItemStack.EMPTY);
                return atlas;
            }
        throw new IllegalStateException("Atlas not in inventory");
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> work) {
        var id = mc.player.getUUID();
        mc.getSingleplayerServer()
                .execute(
                        () -> {
                            try {
                                work.accept(
                                        mc.getSingleplayerServer().getPlayerList().getPlayer(id));
                            } catch (Throwable error) {
                                failure = error;
                            }
                        });
    }

    /** Shared disposable landscape; returns the simulated second player for manual playtests. */
    static ServerPlayer prepare(ServerPlayer p) {
        var level = p.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, p.server);
        level.setDayTime(2500);
        level.setWeatherParameters(6000, 0, false, false);
        p.setGameMode(GameType.CREATIVE);
        p.getInventory().clearContent();
        p.teleportTo(level, 4.5, 64, -8.5, 0, 10);
        // A deterministic real block landscape, surveyed by vanilla's MapItem.update below.
        for (int x = -64; x < 192; x++)
            for (int z = -64; z < 64; z++) {
                int river = 40 + (int) (12 * Math.sin(z / 20.0));
                Block block =
                        Math.abs(x - river) < 6
                                ? Blocks.WATER
                                : Math.abs(x - river) < 9
                                        ? Blocks.SAND
                                        : Math.abs(z - 15) < 2
                                                ? Blocks.DIRT_PATH
                                                : (x < -28 && z > 20
                                                        ? Blocks.PODZOL
                                                        : Blocks.GRASS_BLOCK);
                level.setBlock(new BlockPos(x, 62, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, 63, z), block.defaultBlockState(), 2);
            }
        for (int x = -52; x < -28; x += 8)
            for (int z = -40; z < 40; z += 12) {
                level.setBlock(new BlockPos(x, 64, z), Blocks.OAK_LOG.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, 65, z), Blocks.OAK_LOG.defaultBlockState(), 2);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        if (Math.abs(dx) + Math.abs(dz) < 4)
                            level.setBlock(
                                    new BlockPos(x + dx, 66, z + dz),
                                    Blocks.OAK_LEAVES
                                            .defaultBlockState()
                                            .setValue(LeavesBlock.PERSISTENT, true),
                                    2);
            }
        for (int x = 126; x < 144; x++)
            for (int z = -2; z < 12; z++) {
                if (z < 1 || z > 9 || x < 129 || x > 141)
                    level.setBlock(
                            new BlockPos(x, 63, z), Blocks.COBBLESTONE.defaultBlockState(), 2);
            }
        if (ModList.get().isLoaded("mobilecamp")) {
            var anchor = new BlockPos(-15, 64, 28);
            p.moveTo(Vec3.atBottomCenterOf(anchor.north(2)));
            p.setYRot(0);
            var camp =
                    new ItemStack(
                            BuiltInRegistries.ITEM.get(ResourceLocation.parse("mobilecamp:camp")));
            p.setItemInHand(InteractionHand.MAIN_HAND, camp);
            check(
                    camp.useOn(
                                    new UseOnContext(
                                            p,
                                            InteractionHand.MAIN_HAND,
                                            new BlockHitResult(
                                                    Vec3.atCenterOf(anchor.below()).add(0, .5, 0),
                                                    Direction.UP,
                                                    anchor.below(),
                                                    false)))
                            .consumesAction(),
                    "booth places real C.A.M.P.");
        }
        try {
            IntegrationGameTests.claim(
                    level, new ChunkPos(8, 0).toLong(), "Eastwatch Village", p.getUUID());
        } catch (Exception error) {
            throw new IllegalStateException("Village fixture requires actual Village Deed", error);
        }
        p.teleportTo(level, 4.5, 64, -8.5, 0, 10);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var first = MapItem.create(level, 0, 0, (byte) 0, true, false);
        var second = MapItem.create(level, 128, 0, (byte) 0, true, false);
        for (int i = 0; i < 20; i++) {
            ((MapItem) Items.FILLED_MAP).update(level, p, MapItem.getSavedData(first, level));
            ((MapItem) Items.FILLED_MAP).update(level, p, MapItem.getSavedData(second, level));
        }
        p.getInventory().setItem(9, first);
        p.getInventory().setItem(10, new ItemStack(Items.BOOK));
        p.getInventory().setItem(11, second);
        level.setBlock(TABLE, Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), 3);
        var dimension = level.dimension().location().toString();
        Landmarks.get(p.server)
                .put(
                        p.getUUID(),
                        new Location(
                                Landmarks.ID,
                                "iron-mine",
                                "magicalmap:landmark",
                                "Iron mine",
                                dimension,
                                -26,
                                -24,
                                34,
                                true,
                                "minecraft:iron_pickaxe",
                                0xe8bd64,
                                Optional.of(p.getUUID()),
                                false,
                                ""));
        Landmarks.get(p.server)
                .put(
                        p.getUUID(),
                        new Location(
                                Landmarks.ID,
                                "river-ford",
                                "magicalmap:landmark",
                                "River ford",
                                dimension,
                                44,
                                64,
                                15,
                                true,
                                "minecraft:oak_boat",
                                0x75bec6,
                                Optional.of(p.getUUID()),
                                false,
                                ""));
        peer = createPeer(p);
        ready = true;
        return peer;
    }

    /** Creates only the simulated player, retaining the existing course and human inventory. */
    static ServerPlayer createPeer(ServerPlayer p) {
        var level = p.serverLevel();
        var profile =
                new com.mojang.authlib.GameProfile(
                        UUID.fromString("b889ea1d-09a3-4a16-8174-114a64068e02"), "Surveyor");
        var cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        var peer = new ServerPlayer(p.server, level, profile, cookie.clientInformation());
        var connection =
                new net.minecraft.network.Connection(
                        net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.server.getPlayerList().placeNewPlayer(connection, peer, cookie);
        peer.setGameMode(GameType.CREATIVE);
        peer.setNoGravity(true);
        peer.moveTo(-5.5, 64, -20.5, 90, 0);
        return peer;
    }

    private static void check(boolean value, String description) {
        if (!value) throw new IllegalStateException(description);
        LOG.info("atlas booth: PASS {}", description);
    }

    private static void photo(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(
                mc.gameDirectory,
                name + ".png",
                mc.getMainRenderTarget(),
                m -> LOG.info("atlas booth: {}", m.getString()));
    }
}
