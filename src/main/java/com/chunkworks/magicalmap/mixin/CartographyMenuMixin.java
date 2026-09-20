/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.mixin;

import com.chunkworks.magicalmap.AtlasCartography;
import com.chunkworks.magicalmap.MagicalMap;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CartographyTableMenu.class)
public abstract class CartographyMenuMixin extends AbstractContainerMenu {
    protected CartographyMenuMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Shadow @Final private ResultContainer resultContainer;

    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    private void atlasResult(
            ItemStack first, ItemStack second, ItemStack previous, CallbackInfo ci) {
        if (AtlasCartography.custom(first, second)) {
            var output = AtlasCartography.result(first, second);
            if (!ItemStack.matches(output, previous)) resultContainer.setItem(2, output);
            ((CartographyTableMenu) (Object) this).broadcastChanges();
            ci.cancel();
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void atlasShiftClick(Player player, int index, CallbackInfoReturnable<ItemStack> ci) {
        if (index < 3 || index >= slots.size()) return;
        var slot = slots.get(index);
        var input = slot.getItem();
        int target =
                input.is(MagicalMap.ATLAS.get())
                        ? 0
                        : input.is(Items.BOOK)
                                        || input.is(Items.SHEARS)
                                        || input.is(Items.FILLED_MAP)
                                                && getSlot(0).getItem().is(MagicalMap.ATLAS.get())
                                ? 1
                                : -1;
        if (target < 0) return;
        var copy = input.copy();
        if (!moveItemStackTo(input, target, target + 1, false)) {
            ci.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (input.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, input);
        ci.setReturnValue(copy);
    }
}
