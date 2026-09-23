/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.gametest;

import com.chunkworks.magicalmap.*;
import com.chunkworks.magicalmap.api.*;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

/**
 * Partitions: real menu pickup/quick-move; create/bind/extract; duplicate/full atlas; vanilla
 * clone/scale/lock; saved components/landmarks; private owner/stranger; unknown terrain. Tests use
 * the actual menu, maps, SavedData and game registry, not substitute backends.
 */
@GameTestHolder("magicalmap")
@PrefixGameTestTemplate(false)
public final class AtlasGameTests {
    private static CartographyTableMenu table(GameTestHelper h, Player p) {
        h.setBlock(new BlockPos(2, 2, 2), Blocks.CARTOGRAPHY_TABLE);
        return new CartographyTableMenu(
                1,
                p.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2))));
    }

    private static ItemStack map(GameTestHelper h, int offset) {
        var p = h.absolutePos(new BlockPos(offset, 2, 0));
        return MapItem.create(h.getLevel(), p.getX(), p.getZ(), (byte) 0, true, false);
    }

    @GameTest(template = "arena")
    public void realTableCreatesBindsAndRecoversOriginalSheet(GameTestHelper h) {
        var p = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = table(h, p);
        var original = map(h, 0);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Survey of the coast"));
        var id = original.get(DataComponents.MAP_ID);
        MapItem.getSavedData(original, h.getLevel()).setColor(7, 8, (byte) 42);
        menu.getSlot(0).set(original.copy());
        menu.getSlot(1).set(new ItemStack(Items.BOOK));
        h.assertTrue(
                menu.getSlot(1).mayPlace(new ItemStack(Items.BOOK)),
                "book accepted by actual slot");
        menu.clicked(2, 0, ClickType.PICKUP, p);
        var atlas = menu.getCarried().copy();
        menu.setCarried(ItemStack.EMPTY);
        h.assertTrue(
                atlas.is(MagicalMap.ATLAS.get())
                        && menu.getSlot(0).getItem().isEmpty()
                        && menu.getSlot(1).getItem().isEmpty(),
                "one atlas, both ingredients consumed");
        var bound = AtlasPages.maps(atlas).getFirst();
        h.assertTrue(
                ItemStack.isSameItemSameComponents(bound, original),
                "all original sheet components retained");
        var next = map(h, 128);
        menu.getSlot(0).set(atlas);
        menu.getSlot(1).set(next.copy());
        menu.clicked(2, 0, ClickType.PICKUP, p);
        atlas = menu.getCarried().copy();
        menu.setCarried(ItemStack.EMPTY);
        h.assertTrue(AtlasPages.maps(atlas).size() == 2, "second sheet bound by actual menu");
        menu.getSlot(0).set(atlas);
        menu.getSlot(1).set(new ItemStack(Items.SHEARS));
        menu.clicked(2, 0, ClickType.PICKUP, p);
        h.assertTrue(
                ItemStack.isSameItemSameComponents(menu.getCarried(), next),
                "shears recover exact last map");
        h.assertTrue(
                AtlasPages.maps(menu.getSlot(0).getItem()).size() == 1
                        && menu.getSlot(1).getItem().getDamageValue() == 1,
                "atlas remainder and shear wear retained");
        h.assertTrue(
                h.getLevel().getMapData(id).colors[7 + 8 * 128] == 42,
                "original exploration unchanged");
        h.succeed();
    }

    @GameTest(template = "arena")
    public void duplicatesAreRefusedWithoutConsumingOrLosingInput(GameTestHelper h) {
        var p = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = table(h, p);
        var sheet = map(h, 0);
        var atlas = AtlasPages.create(List.of(sheet));
        menu.getSlot(0).set(atlas);
        menu.getSlot(1).set(sheet.copy());
        h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "duplicate has no output");
        menu.clicked(2, 0, ClickType.PICKUP, p);
        h.assertTrue(
                menu.getCarried().isEmpty()
                        && menu.getSlot(0).getItem() == atlas
                        && menu.getSlot(1).getItem().getCount() == 1,
                "rejected operation preserves inputs");
        h.succeed();
    }

    @GameTest(template = "arena")
    public void vanillaCartographyStillClonesScalesAndLocks(GameTestHelper h) {
        var p = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = table(h, p);
        var sheet = map(h, 0);
        var originalId = sheet.get(DataComponents.MAP_ID);
        MapItem.getSavedData(sheet, h.getLevel()).setColor(4, 4, (byte) 30);
        menu.getSlot(0).set(sheet.copy());
        menu.getSlot(1).set(new ItemStack(Items.MAP));
        menu.clicked(2, 0, ClickType.PICKUP, p);
        h.assertTrue(
                menu.getCarried().getCount() == 2
                        && menu.getCarried().get(DataComponents.MAP_ID).equals(originalId),
                "vanilla clone shares data");
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(0).set(sheet.copy());
        menu.getSlot(1).set(new ItemStack(Items.PAPER));
        menu.clicked(2, 0, ClickType.PICKUP, p);
        h.assertTrue(
                MapItem.getSavedData(menu.getCarried(), h.getLevel()).scale == 1,
                "vanilla scaling executes post processing");
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(0).set(sheet.copy());
        menu.getSlot(1).set(new ItemStack(Items.GLASS_PANE));
        menu.clicked(2, 0, ClickType.PICKUP, p);
        var locked = MapItem.getSavedData(menu.getCarried(), h.getLevel());
        h.assertTrue(
                locked.locked && locked.colors[4 + 4 * 128] == 30, "vanilla lock preserves pixels");
        h.succeed();
    }

    @GameTest(template = "arena")
    public void quickMoveUsesBindingSlotsAndTakesOutputOnce(GameTestHelper h) {
        var p = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = table(h, p);
        var atlas = AtlasPages.create(List.of(map(h, 0)));
        var sheet = map(h, 128);
        p.getInventory().setItem(9, atlas);
        menu.clicked(3, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(
                menu.getSlot(0).getItem().is(MagicalMap.ATLAS.get()),
                "shift atlas goes to top slot");
        p.getInventory().setItem(10, sheet);
        menu.clicked(4, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(
                menu.getSlot(1).getItem().is(Items.FILLED_MAP), "shift map goes to binding slot");
        menu.clicked(2, 0, ClickType.QUICK_MOVE, p);
        int atlases = 0;
        for (var item : p.getInventory().items)
            if (item.is(MagicalMap.ATLAS.get())) {
                atlases++;
                h.assertTrue(AtlasPages.maps(item).size() == 2, "complete output retained");
            }
        h.assertTrue(
                atlases == 1
                        && menu.getSlot(0).getItem().isEmpty()
                        && menu.getSlot(1).getItem().isEmpty(),
                "no quick-move duplication");
        h.succeed();
    }

    @GameTest(template = "arena")
    public void componentAndMapDataRoundTripAndDefensiveCopies(GameTestHelper h) {
        var map = map(h, 0);
        var atlas = AtlasPages.create(List.of(map));
        var registry = h.getLevel().registryAccess();
        var loaded = ItemStack.parse(registry, atlas.save(registry)).orElseThrow();
        h.assertTrue(
                ItemStack.isSameItemSameComponents(atlas, loaded),
                "real component persistence roundtrip");
        var extracted = AtlasPages.maps(loaded).getFirst();
        extracted.setCount(9);
        h.assertTrue(
                AtlasPages.maps(loaded).getFirst().getCount() == 1,
                "representation protected from mutation");
        var data = MapItem.getSavedData(map, h.getLevel());
        data.setColor(0, 0, (byte) 25);
        var restored =
                net.minecraft.world.level.saveddata.maps.MapItemSavedData.load(
                        data.save(new CompoundTag(), registry), registry);
        h.assertTrue(
                restored.colors[0] == 25
                        && restored.centerX == data.centerX
                        && restored.dimension.equals(data.dimension),
                "map pixels and geometry survive persistence");
        h.succeed();
    }

    @GameTest(template = "arena")
    public void landmarksPersistPrivatelyAndUnknownTerrainStaysUncharted(GameTestHelper h) {
        var owner = UUID.randomUUID();
        var stranger = UUID.randomUUID();
        var store = new Landmarks();
        var dimension = h.getLevel().dimension().location().toString();
        var location =
                new Location(
                        Landmarks.ID,
                        "mine",
                        "magicalmap:landmark",
                        "Deep iron",
                        dimension,
                        -20,
                        -45,
                        10,
                        true,
                        "minecraft:iron_pickaxe",
                        0xaabbcc,
                        Optional.of(owner),
                        true,
                        "");
        store.put(owner, location);
        var restored =
                Landmarks.load(
                        store.save(new CompoundTag(), h.getLevel().registryAccess()),
                        h.getLevel().registryAccess());
        h.assertTrue(
                restored.snapshot(new Viewer(owner, dimension, false)).equals(List.of(location)),
                "private landmark preserves depth and name");
        h.assertTrue(
                restored.snapshot(new Viewer(stranger, dimension, true)).isEmpty(),
                "operator is not automatically owner of private bookmarks");
        var map = map(h, 0);
        var atlas = AtlasPages.create(List.of(map));
        var sheet = AtlasPages.sheets(atlas, h.getLevel()).getFirst();
        h.assertTrue(
                !AtlasPages.charted(atlas, h.getLevel(), dimension, sheet.left(), sheet.top()),
                "blank pixels remain uncharted");
        MapItem.getSavedData(map, h.getLevel()).setColor(0, 0, (byte) 30);
        h.assertTrue(
                AtlasPages.charted(atlas, h.getLevel(), dimension, sheet.left(), sheet.top()),
                "only recorded pixel is charted");
        h.assertTrue(
                !AtlasPages.charted(atlas, h.getLevel(), dimension, sheet.left() + 1, sheet.top()),
                "neighbor stays blank");
        h.succeed();
    }
}
