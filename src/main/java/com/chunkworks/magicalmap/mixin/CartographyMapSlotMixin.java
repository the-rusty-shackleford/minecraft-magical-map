/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.mixin;

import com.chunkworks.magicalmap.MagicalMap;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$3")
public abstract class CartographyMapSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void atlasInput(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (stack.is(MagicalMap.ATLAS.get())) ci.setReturnValue(true);
    }
}
