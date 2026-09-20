/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.mixin;

import net.minecraft.world.item.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$4")
public abstract class CartographyMaterialSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void atlasMaterial(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (stack.is(Items.BOOK) || stack.is(Items.FILLED_MAP) || stack.is(Items.SHEARS))
            ci.setReturnValue(true);
    }
}
