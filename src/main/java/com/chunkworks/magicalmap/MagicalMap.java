/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

/** Composition root. Providers and server state never depend on client classes. */
@Mod("magicalmap")
public final class MagicalMap {
    public static final String ID = "magicalmap";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredItem<AtlasItem> ATLAS =
            ITEMS.register("atlas", () -> new AtlasItem(new Item.Properties().stacksTo(1)));

    public MagicalMap(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(Payloads::register);
        bus.addListener(
                (BuildCreativeModeTabContentsEvent event) -> {
                    if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
                        event.accept(ATLAS);
                });
        NeoForge.EVENT_BUS.register(AtlasServer.class);
    }
}
