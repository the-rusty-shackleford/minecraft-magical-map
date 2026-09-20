/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.client;

import com.chunkworks.magicalmap.AtlasPages;
import com.chunkworks.magicalmap.api.Location;
import com.chunkworks.magicalmap.domain.*;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

import java.util.*;

/**
 * One map renderer shared by atlas and HUD. Pixel geometry derives solely from actual map scale.
 */
public final class AtlasCanvas {
    static final int INK = 0xff3a3528, MUTED = 0xff73694d, PAPER = 0xffd8c895, GOLD = 0xffd8b969;
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private AtlasCanvas() {}

    record Hit(int x, int y, List<Location.Key> ids) {}

    record Bounds(int x, int y, int width, int height) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + width && my < y + height;
        }
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        g.fill(x, y, x + w, y + h, 0xff28251b);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xff83714d);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PAPER);
        g.hLine(x + 4, x + w - 5, y + 3, 0xfff0dfab);
        for (int dx : new int[] {5, w - 8})
            for (int dy : new int[] {5, h - 8})
                g.fill(x + dx, y + dy, x + dx + 2, y + dy + 2, 0xffb39658);
    }

    static List<Hit> draw(
            GuiGraphics g,
            Bounds box,
            String dimension,
            double centerX,
            double centerZ,
            double blocksPerPixel,
            boolean compact) {
        var mc = Minecraft.getInstance();
        g.fill(box.x, box.y, box.x + box.width, box.y + box.height, 0xffc8b98d);
        g.enableScissor(box.x, box.y, box.x + box.width, box.y + box.height);
        // Blank parchment is not terrain. Grid is anchored in world space, so pans do not swim.
        int grid = Math.max(16, (int) (128 / blocksPerPixel));
        int gx = (int) Math.floor(box.width / 2.0 - centerX / blocksPerPixel),
                gz = (int) Math.floor(box.height / 2.0 - centerZ / blocksPerPixel);
        for (int x = Math.floorMod(gx, grid); x < box.width; x += grid)
            g.vLine(box.x + x, box.y, box.y + box.height, 0xffb9ab82);
        for (int y = Math.floorMod(gz, grid); y < box.height; y += grid)
            g.hLine(box.x, box.x + box.width, box.y + y, 0xffb9ab82);
        RenderSystem.enableBlend();
        for (var sheet : AtlasClient.drawSheets) {
            var texture = AtlasClient.textures.get(sheet.id());
            if (texture == null || !sheet.dimension().equals(dimension)) continue;
            int left = screenX(box, centerX, blocksPerPixel, sheet.left()),
                    top = screenY(box, centerZ, blocksPerPixel, sheet.top());
            int right = screenX(box, centerX, blocksPerPixel, sheet.left() + 128 * sheet.step());
            int bottom = screenY(box, centerZ, blocksPerPixel, sheet.top() + 128 * sheet.step());
            if (right < box.x
                    || left > box.x + box.width
                    || bottom < box.y
                    || top > box.y + box.height) continue;
            g.blit(texture.id, left, top, right - left, bottom - top, 0, 0, 128, 128, 128, 128);
            if (!compact) g.renderOutline(left, top, right - left, bottom - top, 0x44746346);
        }
        var groups = new LinkedHashMap<Long, List<Location.Key>>();
        var coordinates = new HashMap<Location.Key, int[]>();
        for (var entry : AtlasClient.places.entrySet()) {
            var moving = entry.getValue();
            var p = moving.value;
            if (!p.dimension().equals(dimension)) continue;
            boolean self =
                    mc.player != null
                            && p.kind().equals("magicalmap:player")
                            && p.owner().equals(Optional.of(mc.player.getUUID()));
            double wx = self ? mc.player.getX() : moving.x(),
                    wz = self ? mc.player.getZ() : moving.z();
            int x = screenX(box, centerX, blocksPerPixel, wx),
                    y = screenY(box, centerZ, blocksPerPixel, wz);
            if (!box.contains(x, y)) continue;
            long bucket =
                    ((long) Math.floorDiv(x, 18) << 32) | (Math.floorDiv(y, 18) & 0xffffffffL);
            groups.computeIfAbsent(bucket, unused -> new ArrayList<>()).add(entry.getKey());
            coordinates.put(entry.getKey(), new int[] {x, y});
        }
        var hits = new ArrayList<Hit>();
        for (var ids : groups.values()) {
            var key = ids.contains(AtlasClient.selected) ? AtlasClient.selected : ids.getFirst();
            int[] pos = coordinates.get(key);
            var location = AtlasClient.places.get(key).value;
            icon(g, location, pos[0] - 7, pos[1] - 7, 14, key.equals(AtlasClient.selected));
            if (ids.size() > 1)
                g.drawString(mc.font, "" + ids.size(), pos[0] + 4, pos[1] + 3, 0xffffffff, true);
            hits.add(new Hit(pos[0], pos[1], List.copyOf(ids)));
        }
        if (mc.player != null
                && mc.player.level().dimension().location().toString().equals(dimension)) {
            int x = screenX(box, centerX, blocksPerPixel, mc.player.getX()),
                    y = screenY(box, centerZ, blocksPerPixel, mc.player.getZ());
            double yaw = Math.toRadians(mc.player.getYRot());
            for (int d = 9; d <= 15; d++) {
                int px = (int) Math.round(x - Math.sin(yaw) * d),
                        py = (int) Math.round(y + Math.cos(yaw) * d);
                g.fill(px - 1, py - 1, px + 2, py + 2, d < 13 ? 0xff293b34 : 0xfff5eac7);
            }
        }
        g.disableScissor();
        g.renderOutline(box.x, box.y, box.width, box.height, 0xff776846);
        g.drawString(mc.font, "N", box.x + box.width / 2 - 3, box.y + 4, INK, false);
        return hits;
    }

    static int screenX(Bounds b, double center, double scale, double x) {
        return b.x + (int) Math.round(b.width / 2.0 + (x - center) / scale);
    }

    static int screenY(Bounds b, double center, double scale, double z) {
        return b.y + (int) Math.round(b.height / 2.0 + (z - center) / scale);
    }

    static void icon(GuiGraphics g, Location place, int x, int y, int size, boolean selected) {
        var mc = Minecraft.getInstance();
        g.fill(x - 2, y - 2, x + size + 2, y + size + 2, selected ? 0xfff1d17b : 0xcc302d22);
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xff000000 | place.color());
        if (place.kind().equals("magicalmap:player") && place.owner().isPresent()) {
            var id = place.owner().get();
            var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
            var skin = info == null ? DefaultPlayerSkin.get(id) : info.getSkin();
            PlayerFaceRenderer.draw(g, skin.texture(), x, y, size, true, false);
        } else {
            var item =
                    ICONS.computeIfAbsent(
                            place.icon(),
                            id -> {
                                var found = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
                                return new ItemStack(found == Items.AIR ? Items.MAP : found);
                            });
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(size / 16f, size / 16f, 1);
            g.renderItem(item, 0, 0);
            g.pose().popPose();
        }
    }

    static String dimensionName(String id) {
        String name = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        if (name.equals("the nether")) return "Nether";
        if (name.equals("the end")) return "The End";
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    static String distance(Location location) {
        var player = Minecraft.getInstance().player;
        if (player == null) return "";
        if (!player.level().dimension().location().toString().equals(location.dimension()))
            return dimensionName(location.dimension());
        int distance =
                (int)
                        Math.round(
                                Navigation.distance(
                                        player.getX(), player.getZ(), location.x(), location.z()));
        return distance
                + " blocks "
                + Navigation.heading(
                        Navigation.bearing(
                                player.getX(), player.getZ(), location.x(), location.z()));
    }

    /**
     * requires: HUD render; effects: draws travel view only, never changes input focus; throws:
     * none.
     */
    public static void hud(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.options.hideGui
                || !AtlasClient.travel
                || mc.screen != null
                || AtlasPages.held(mc.player).isEmpty()) return;
        int w = 142, h = 150, x = g.guiWidth() - w - 9, y = 9;
        frame(g, x, y, w, h);
        g.drawString(mc.font, "MAGICAL ATLAS", x + 9, y + 9, INK, false);
        var box = new Bounds(x + 7, y + 23, w - 14, 100);
        draw(
                g,
                box,
                mc.player.level().dimension().location().toString(),
                mc.player.getX(),
                mc.player.getZ(),
                2,
                true);
        g.drawString(
                mc.font,
                Navigation.heading(mc.player.getYRot())
                        + "  "
                        + mc.player.getBlockX()
                        + ", "
                        + mc.player.getBlockZ(),
                x + 9,
                y + 129,
                INK,
                false);
        var target = AtlasClient.tracked();
        if (target != null) {
            frame(g, x, y + h + 3, w, 34);
            g.drawString(
                    mc.font,
                    mc.font.plainSubstrByWidth(target.name(), w - 16),
                    x + 8,
                    y + h + 10,
                    INK,
                    false);
            g.drawString(mc.font, distance(target), x + 8, y + h + 21, MUTED, false);
        }
    }
}
