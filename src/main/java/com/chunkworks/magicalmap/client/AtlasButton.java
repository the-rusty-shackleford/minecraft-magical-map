/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Pixel-edged atlas control retaining vanilla focus, narration and keyboard interaction. */
final class AtlasButton extends Button {
    AtlasButton(int x, int y, int width, int height, String label, Runnable action) {
        super(
                x,
                y,
                width,
                height,
                Component.literal(label),
                unused -> action.run(),
                DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int fill = isHoveredOrFocused() ? 0xff55756b : 0xff344e47;
        if (!active) fill = 0xff635e4d;
        g.fill(x, y, x + w, y + h, 0xff201e17);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.hLine(x + 2, x + w - 3, y + 1, isHoveredOrFocused() ? 0xffa7c5a9 : 0xff738873);
        g.drawCenteredString(
                Minecraft.getInstance().font,
                getMessage(),
                x + w / 2,
                y + (h - 8) / 2,
                active ? 0xfff4e7c2 : 0xffaaa18b);
    }
}
