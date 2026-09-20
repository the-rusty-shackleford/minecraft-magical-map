/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.client;

import com.chunkworks.magicalmap.*;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import org.lwjgl.glfw.GLFW;

/** Client-only composition root; ordinary travel view never opens a screen or captures inputs. */
@EventBusSubscriber(modid = MagicalMap.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    public static final KeyMapping OPEN =
            new KeyMapping(
                    "key.magicalmap.open",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_M,
                    "key.categories.magicalmap");
    public static final KeyMapping TRAVEL =
            new KeyMapping(
                    "key.magicalmap.travel",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_N,
                    "key.categories.magicalmap");

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
        event.register(TRAVEL);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(
                () -> {
                    Payloads.openAtlas = AtlasClient::open;
                    Payloads.receiveState = AtlasClient::state;
                    Payloads.receiveTerrain = AtlasClient::terrain;
                    Payloads.receiveNotice = p -> AtlasClient.notice(p.message());
                });
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(MagicalMap.ID, "travel"),
                AtlasCanvas::hud);
    }
}
