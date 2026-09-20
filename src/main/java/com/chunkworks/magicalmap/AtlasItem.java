/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

/** Physical atlas. AF: a recoverable collection of real map stacks; RI: managed by AtlasPages. */
public final class AtlasItem extends Item {
    public AtlasItem(Properties properties) {
        super(properties);
    }

    /** requires: actual hand; effects: opens atlas on client, no item consumption; throws: none. */
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) Payloads.openAtlas.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        try {
            lines.add(
                    Component.literal(
                                    AtlasPages.maps(stack).size()
                                            + " / "
                                            + AtlasPages.LIMIT
                                            + " map sheets")
                            .withColor(0xc6b98d));
        } catch (IllegalArgumentException invalid) {
            lines.add(
                    Component.literal("Invalid map data; contents preserved").withColor(0xdf907c));
        }
        lines.add(Component.literal("Hold to navigate • Use to open").withColor(0xaaaaaa));
        lines.add(Component.literal("Bind maps at a cartography table").withColor(0xaaaaaa));
    }
}
