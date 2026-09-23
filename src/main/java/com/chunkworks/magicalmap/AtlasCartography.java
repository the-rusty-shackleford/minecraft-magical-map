/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.*;

/** Custom table recipes. Vanilla scaling/cloning/locking remain on original code paths. */
public final class AtlasCartography {
    private AtlasCartography() {}

    /** requires: table inputs; effects: identifies atlas-specific operation; throws: none. */
    public static boolean custom(ItemStack first, ItemStack second) {
        return first.is(MagicalMap.ATLAS.get())
                || first.is(Items.FILLED_MAP) && second.is(Items.BOOK);
    }

    /**
     * requires: table inputs, the level of the player at the table; effects: computes the output
     * without changing the inputs or the world. On the server a map whose cell the atlas already
     * charts folds, so the result is the atlas unchanged (D-0006); the client cannot tell cells
     * apart (its map data carries no centre), so it predicts a bind and the server's slot update
     * corrects it; throws: none.
     */
    public static ItemStack result(ItemStack first, ItemStack second, Level level) {
        try {
            if (first.is(Items.FILLED_MAP) && second.is(Items.BOOK))
                return AtlasPages.create(List.of(first.copyWithCount(1)));
            if (first.is(MagicalMap.ATLAS.get())) {
                if (second.is(Items.FILLED_MAP))
                    return level instanceof ServerLevel server
                                    && AtlasPages.absorption(first, second, server).isPresent()
                            ? first.copyWithCount(1)
                            : AtlasPages.add(first, second);
                var maps = AtlasPages.maps(first);
                if (second.is(Items.SHEARS) && !maps.isEmpty()) return maps.getLast();
            }
        } catch (IllegalArgumentException rejected) {
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /**
     * requires: real server-validated output take; effects: consumes inputs once or returns the
     * remaining atlas and damages shears; creative shears retain durability; on the server, a
     * folded map's charted pixels join the sheet that charts its cell before it is consumed;
     * throws: none.
     */
    public static void take(CartographyTableMenu menu, Player player) {
        var first = menu.getSlot(0).getItem();
        var second = menu.getSlot(1).getItem();
        if (first.is(MagicalMap.ATLAS.get())
                && second.is(Items.FILLED_MAP)
                && player.level() instanceof ServerLevel level)
            AtlasPages.absorption(first, second, level)
                    .ifPresent(fold -> AtlasPages.merge(level, List.of(fold)));
        if (first.is(MagicalMap.ATLAS.get()) && second.is(Items.SHEARS)) {
            var maps = new ArrayList<>(AtlasPages.maps(first));
            maps.removeLast();
            var remaining = first.copyWithCount(1);
            remaining.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(maps));
            menu.getSlot(0).set(remaining);
            if (!player.getAbilities().instabuild) {
                second.setDamageValue(second.getDamageValue() + 1);
                if (second.getDamageValue() >= second.getMaxDamage()) second.shrink(1);
                menu.getSlot(1).setChanged();
            }
        } else {
            menu.getSlot(0).remove(1);
            menu.getSlot(1).remove(1);
        }
        player.level()
                .playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                        SoundSource.BLOCKS,
                        1,
                        1);
        menu.slotsChanged(menu.container);
        menu.broadcastChanges();
    }
}
