/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import com.chunkworks.magicalmap.domain.Sheet;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Atlas component boundary. AF: ordered map stacks retaining original map IDs and components. RI:
 * at most 64 nonempty count-one maps with distinct IDs; no exposed mutable representation.
 * Malformed components are rejected instead of silently discarding items.
 */
public final class AtlasPages {
    public static final int LIMIT = 64;

    private AtlasPages() {}

    /**
     * requires: item stack; effects: returns defensive copies, or empty for non-atlases; throws:
     * malformed atlas contents.
     */
    public static List<ItemStack> maps(ItemStack atlas) {
        if (!atlas.is(MagicalMap.ATLAS.get())) return List.of();
        var maps =
                atlas.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).stream()
                        .toList();
        var ids = new HashSet<Integer>();
        if (maps.size() > LIMIT) throw new IllegalArgumentException("Atlas is too large");
        for (var map : maps) {
            var id = map.get(DataComponents.MAP_ID);
            if (!map.is(Items.FILLED_MAP) || map.getCount() != 1 || id == null || !ids.add(id.id()))
                throw new IllegalArgumentException("Atlas contains invalid or duplicate sheets");
        }
        return maps;
    }

    /**
     * requires: valid maps; effects: returns a new atlas retaining map identity; throws: invalid
     * maps.
     */
    public static ItemStack create(List<ItemStack> maps) {
        var atlas = new ItemStack(MagicalMap.ATLAS.get());
        atlas.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(maps));
        maps(atlas);
        return atlas;
    }

    /**
     * requires: valid atlas/maps; effects: returns new atlas with appended sheet, preserving custom
     * name; throws: too many/duplicate/invalid sheets.
     */
    public static ItemStack add(ItemStack atlas, ItemStack map) {
        var pages = new ArrayList<>(maps(atlas));
        pages.add(map.copyWithCount(1));
        var result = atlas.copyWithCount(1);
        result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(pages));
        maps(result);
        return result;
    }

    /**
     * requires: valid atlas; effects: returns immutable available sheet metadata, without chunk
     * access; throws: invalid atlas. Missing world map data remains recoverable as an item but is
     * not rendered.
     */
    public static List<Sheet> sheets(ItemStack atlas, Level level) {
        var result = new ArrayList<Sheet>();
        for (var map : maps(atlas)) {
            var data = MapItem.getSavedData(map, level);
            if (data != null)
                result.add(
                        new Sheet(
                                map.get(DataComponents.MAP_ID).id(),
                                data.dimension.location().toString(),
                                data.centerX,
                                data.centerZ,
                                data.scale,
                                data.locked));
        }
        return List.copyOf(result);
    }

    /** requires: player; effects: selects main-hand atlas then offhand atlas; throws: none. */
    public static ItemStack held(Player player) {
        if (player.getMainHandItem().is(MagicalMap.ATLAS.get())) return player.getMainHandItem();
        if (player.getOffhandItem().is(MagicalMap.ATLAS.get())) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /**
     * requires: actual held atlas; effects: checks a recorded pixel, never terrain/world chunks;
     * throws: invalid atlas.
     */
    public static boolean charted(
            ItemStack atlas, Level level, String dimension, double x, double z) {
        for (var sheet : sheets(atlas, level)) {
            int pixel = sheet.pixel(x, z);
            if (sheet.dimension().equals(dimension)
                    && pixel >= 0
                    && (level.getMapData(
                                                            new net.minecraft.world.level.saveddata
                                                                    .maps.MapId(sheet.id()))
                                                    .colors[pixel]
                                            & 255)
                                    / 4
                            != 0) return true;
        }
        return false;
    }
}
