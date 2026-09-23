/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import com.chunkworks.magicalmap.domain.Folding;
import com.chunkworks.magicalmap.domain.Sheet;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;

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
            var sheet = sheet(map, level);
            if (sheet != null) result.add(sheet);
        }
        return List.copyOf(result);
    }

    /**
     * requires: item stack; effects: returns the sheet of a filled map whose data this level
     * knows, else null; throws: none.
     */
    public static Sheet sheet(ItemStack map, Level level) {
        var id = map.get(DataComponents.MAP_ID);
        var data = map.is(Items.FILLED_MAP) && id != null ? MapItem.getSavedData(map, level) : null;
        return data == null
                ? null
                : new Sheet(
                        id.id(),
                        data.dimension.location().toString(),
                        data.centerX,
                        data.centerZ,
                        data.scale,
                        data.locked);
    }

    /**
     * requires: valid atlas, item stack, server level (client map data carries no centre, so only
     * the server can tell cells apart); effects: returns the fold of the map onto the atlas sheet
     * charting the same cell (D-0006), or empty when the map's cell is new to the atlas, the
     * map's id is already inside, or the map has no saved data; throws: invalid atlas.
     */
    public static Optional<Folding.Fold> absorption(
            ItemStack atlas, ItemStack map, ServerLevel level) {
        var added = sheet(map, level);
        if (added == null) return Optional.empty();
        var sheets = new ArrayList<>(sheets(atlas, level));
        for (var sheet : sheets) if (sheet.id() == added.id()) return Optional.empty();
        sheets.add(added);
        return Folding.plan(sheets).stream().filter(f -> f.spare() == added.id()).findFirst();
    }

    /**
     * requires: valid atlas; effects: returns a new atlas without the maps of the given ids,
     * keeping order and every other component, or the same atlas when none is inside; throws:
     * invalid atlas.
     */
    public static ItemStack without(ItemStack atlas, Set<Integer> ids) {
        var maps = maps(atlas);
        var kept = new ArrayList<ItemStack>();
        for (var map : maps) if (!ids.contains(map.get(DataComponents.MAP_ID).id())) kept.add(map);
        if (kept.size() == maps.size()) return atlas;
        var result = atlas.copyWithCount(1);
        result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(kept));
        return result;
    }

    /**
     * requires: server level; effects: for each fold whose two sheets this level knows, copies
     * every charted pixel of the spare onto the uncharted pixels of the kept sheet and marks the
     * kept sheet dirty; a pixel the kept sheet already charts stays as it is; banner and frame
     * markers do not carry over; returns pixels copied; throws: none.
     */
    public static int merge(ServerLevel level, List<Folding.Fold> folds) {
        int copied = 0;
        for (var fold : folds) {
            var kept = level.getMapData(new MapId(fold.kept()));
            var spare = level.getMapData(new MapId(fold.spare()));
            if (kept == null || spare == null) continue;
            for (int i = 0; i < 128 * 128; i++)
                if (kept.colors[i] == 0
                        && spare.colors[i] != 0
                        && kept.updateColor(i % 128, i / 128, spare.colors[i])) copied++;
        }
        return copied;
    }

    /**
     * requires: player on the server; effects: if the held atlas charts any cell twice, folds
     * every later sheet of a cell onto the cell's first sheet, replaces the held atlas with one
     * without the spare maps and returns how many folded; otherwise changes nothing and returns
     * 0; throws: invalid atlas.
     */
    public static int foldHeld(Player player, ServerLevel level) {
        var atlas = held(player);
        if (atlas.isEmpty()) return 0;
        var folds = Folding.plan(sheets(atlas, level));
        if (folds.isEmpty()) return 0;
        merge(level, folds);
        var hand =
                player.getMainHandItem() == atlas
                        ? InteractionHand.MAIN_HAND
                        : InteractionHand.OFF_HAND;
        player.setItemInHand(hand, without(atlas, Folding.spares(folds)));
        return folds.size();
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
