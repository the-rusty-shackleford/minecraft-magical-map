/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.gametest;

import com.chunkworks.magicalmap.MagicalMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Partitions: the mod's own creative tab lists the atlas; the vanilla Tools & Utilities tab still
 * lists it. Built through the real tab-content rebuild, not a stub. */
@GameTestHolder("magicalmap")
@PrefixGameTestTemplate(false)
public final class TabGameTests {
    @GameTest(template = "arena")
    public void creativeTabsListTheAtlas(GameTestHelper h) {
        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, h.getLevel().registryAccess());
        h.assertTrue(MagicalMap.TAB.get().getDisplayItems().stream().anyMatch(s -> s.is(MagicalMap.ATLAS.get())), "Magical Map tab lists the atlas");
        h.assertTrue(MagicalMap.TAB.get().getDisplayItems().size() == 1, "Magical Map tab lists nothing else");
        var tools = h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB).getOrThrow(CreativeModeTabs.TOOLS_AND_UTILITIES);
        h.assertTrue(tools.getDisplayItems().stream().anyMatch((ItemStack s) -> s.is(MagicalMap.ATLAS.get())), "Tools & Utilities still lists the atlas");
        h.succeed();
    }
}
