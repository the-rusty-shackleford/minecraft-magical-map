/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.mixin;

import com.chunkworks.magicalmap.AtlasCartography;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$5")
public abstract class CartographyOutputMixin {
    @Shadow @Final private CartographyTableMenu this$0;

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void atlasTake(Player player, ItemStack result, CallbackInfo ci) {
        if (AtlasCartography.custom(this$0.getSlot(0).getItem(), this$0.getSlot(1).getItem())) {
            AtlasCartography.take(this$0, player);
            ci.cancel();
        }
    }
}
