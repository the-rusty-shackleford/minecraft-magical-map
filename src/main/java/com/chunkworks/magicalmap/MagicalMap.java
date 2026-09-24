/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

/** Composition root. Providers and server state never depend on client classes. */
@Mod("magicalmap")
public final class MagicalMap {
    public static final String ID = "magicalmap";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredItem<AtlasItem> ATLAS =
            ITEMS.register("atlas", () -> new AtlasItem(new Item.Properties().stacksTo(1)));
    /** The mod's own creative tab beside the vanilla ones; the atlas also stays in Tools & Utilities. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.magicalmap"))
            .icon(() -> ATLAS.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(ATLAS.get()))
            .build());

    public MagicalMap(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
        bus.addListener(Payloads::register);
        bus.addListener(
                (BuildCreativeModeTabContentsEvent event) -> {
                    if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
                        event.accept(ATLAS);
                });
        NeoForge.EVENT_BUS.register(AtlasServer.class);
        // The atlas is an Azimuth provider when Azimuth is present (D-0007); the bridge class is
        // the only one naming Azimuth, and it is loaded only past this check.
        if (ModList.get().isLoaded("azimuth")) com.chunkworks.magicalmap.integration.azimuth.AzimuthBridge.register();
    }
}
