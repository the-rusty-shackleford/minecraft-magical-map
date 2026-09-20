/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.client;

import com.chunkworks.magicalmap.*;
import com.chunkworks.magicalmap.api.Location;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Named personal marker editor. Map-click points explicitly have unknown altitude. */
public final class LandmarkScreen extends Screen {
    private static final String[] ICONS = {
        "minecraft:iron_pickaxe",
        "minecraft:torch",
        "minecraft:chest",
        "minecraft:oak_sapling",
        "minecraft:diamond",
        "minecraft:ender_eye",
        "minecraft:red_bed",
        "minecraft:filled_map"
    };
    private static final String[] NAMES = {
        "Mine", "Cave", "Storage", "Grove", "Treasure", "Portal", "Home", "Place"
    };
    private static final int[] COLORS = {
        0xe8bd64, 0x9dc27c, 0x75bec6, 0xc99dd6, 0xde927b, 0xe4dbc0
    };
    private final Screen parent;
    private final Location original;
    private final String dimension;
    private final double x, z;
    private final boolean here;
    private EditBox name;
    private int icon, color;
    private AtlasButton save;

    public LandmarkScreen(
            Screen parent, Location original, String dimension, double x, double z, boolean here) {
        super(Component.literal(original == null ? "New landmark" : "Edit landmark"));
        this.parent = parent;
        this.original = original;
        this.dimension = dimension;
        this.x = x;
        this.z = z;
        this.here = here;
        if (original != null) {
            icon = Math.max(0, Arrays.asList(ICONS).indexOf(original.icon()));
            for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == original.color()) color = i;
        }
    }

    @Override
    protected void init() {
        int left = width / 2 - 132, top = height / 2 - 98;
        name =
                addRenderableWidget(
                        new EditBox(
                                font,
                                left + 17,
                                top + 42,
                                230,
                                20,
                                Component.literal("Landmark name")));
        name.setMaxLength(64);
        name.setValue(original == null ? "" : original.name());
        name.setHint(Component.literal("e.g. Northern iron mine"));
        setInitialFocus(name);
        addRenderableWidget(
                new AtlasButton(
                        left + 16,
                        top + 78,
                        112,
                        21,
                        "Icon: " + NAMES[icon],
                        () -> {
                            icon = (icon + 1) % ICONS.length;
                            rebuildWidgetsPreservingName();
                        }));
        addRenderableWidget(
                new AtlasButton(
                        left + 136,
                        top + 78,
                        112,
                        21,
                        "Change color",
                        () -> {
                            color = (color + 1) % COLORS.length;
                        }));
        save =
                addRenderableWidget(
                        new AtlasButton(
                                left + 16, top + 157, 112, 22, "Save landmark", this::save));
        addRenderableWidget(
                new AtlasButton(left + 136, top + 157, 112, 22, "Cancel", this::onClose));
    }

    private void rebuildWidgetsPreservingName() {
        String text = name.getValue();
        rebuildWidgets();
        name.setValue(text);
    }

    private void save() {
        try {
            String text = Location.text(name.getValue().strip(), 64);
            AtlasClient.send(
                    original != null
                            ? Payloads.Operation.EDIT
                            : here ? Payloads.Operation.ADD_HERE : Payloads.Operation.ADD_POINT,
                    original,
                    text,
                    ICONS[icon],
                    COLORS[color],
                    dimension,
                    x,
                    z);
            onClose();
        } catch (IllegalArgumentException invalid) {
            AtlasClient.notice("Give this landmark a name.");
        }
    }

    @Override
    public void tick() {
        if (minecraft.player == null || AtlasPages.held(minecraft.player).isEmpty())
            minecraft.setScreen(null);
        save.active = !name.getValue().isBlank();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER && save.active) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xbb122019);
        int left = width / 2 - 132, top = height / 2 - 98;
        AtlasCanvas.frame(g, left, top, 264, 196);
        g.drawString(font, title, left + 17, top + 17, AtlasCanvas.INK, false);
        String position =
                here
                        ? "Your current position, including depth"
                        : AtlasCanvas.dimensionName(dimension) + "  " + (int) x + ", " + (int) z;
        g.drawString(
                font,
                font.plainSubstrByWidth(position, 230),
                left + 17,
                top + 111,
                AtlasCanvas.MUTED,
                false);
        g.drawString(font, "Visible only to you", left + 17, top + 126, AtlasCanvas.MUTED, false);
        g.fill(left + 224, top + 128, left + 242, top + 139, 0xff000000 | COLORS[color]);
        super.render(g, mouseX, mouseY, delta);
    }
}
