/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.client;

import com.chunkworks.magicalmap.*;
import com.chunkworks.magicalmap.api.Location;
import com.chunkworks.magicalmap.domain.Sheet;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Render-thread state. AF: current authorized atlas snapshot plus interpolation/texture caches. RI:
 * session-gated payloads; <=64 textures, closed on reset/disconnect; immutable public views.
 */
@EventBusSubscriber(modid = MagicalMap.ID, value = Dist.CLIENT)
public final class AtlasClient {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Magical Map");
    static long session;
    static List<Sheet> sheets = List.of();
    static List<Sheet> drawSheets = List.of();
    static long revision;
    static final Map<Integer, Texture> textures = new HashMap<>();
    static final Map<Location.Key, MovingPlace> places = new LinkedHashMap<>();
    static Location.Key selected, tracked;
    static boolean travel = true;
    static String notice = "";
    static long noticeUntil;

    private AtlasClient() {}

    static final class Texture {
        final ResourceLocation id;
        final DynamicTexture texture;
        byte[] colors;

        Texture(int mapId, byte[] colors) {
            id = ResourceLocation.fromNamespaceAndPath(MagicalMap.ID, "sheet/" + mapId);
            texture = new DynamicTexture(new NativeImage(128, 128, false));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            update(colors);
        }

        void update(byte[] colors) {
            this.colors = colors;
            var image = texture.getPixels();
            for (int y = 0; y < 128; y++)
                for (int x = 0; x < 128; x++) {
                    int color = colors[x + y * 128] & 255;
                    image.setPixelRGBA(
                            x, y, color / 4 == 0 ? 0 : MapColor.getColorFromPackedId(color));
                }
            texture.upload();
        }
    }

    static final class MovingPlace {
        Location value;
        double oldX, oldZ;
        long since;

        MovingPlace(Location value) {
            this.value = value;
            oldX = value.x();
            oldZ = value.z();
            since = Util.getMillis();
        }

        double x() {
            return oldX + (value.x() - oldX) * fraction();
        }

        double z() {
            return oldZ + (value.z() - oldZ) * fraction();
        }

        double fraction() {
            return Math.min(1, (Util.getMillis() - since) / 250.0);
        }

        void update(Location next) {
            boolean jump =
                    !next.dimension().equals(value.dimension())
                            || Math.hypot(next.x() - value.x(), next.z() - value.z()) > 128;
            oldX = jump ? next.x() : x();
            oldZ = jump ? next.z() : z();
            value = next;
            since = Util.getMillis();
        }
    }

    static void state(Payloads.State packet) {
        if (packet.reset()) {
            LOG.debug("atlas session {} -> {}: {} sheets, selection {} dropped", session, packet.session(), packet.sheets().size(), selected);
            clear();
            session = packet.session();
            sheets = packet.sheets();
            drawSheets =
                    sheets.stream()
                            .sorted(Comparator.comparingInt(Sheet::scale).reversed())
                            .toList();
        }
        if (packet.session() != session) return;
        revision++;
        for (var id : packet.removed()) {
            places.remove(id);
            if (id.equals(selected)) {
                LOG.debug("atlas selection {} removed by the server", id);
                selected = null;
            }
            if (id.equals(tracked)) tracked = null;
        }
        for (var place : packet.upserts()) {
            var old = places.get(place.key());
            if (old == null) places.put(place.key(), new MovingPlace(place));
            else old.update(place);
        }
    }

    static void terrain(Payloads.Terrain packet) {
        if (packet.session() != session || sheets.stream().noneMatch(s -> s.id() == packet.mapId()))
            return;
        var texture = textures.get(packet.mapId());
        if (texture == null)
            textures.put(packet.mapId(), new Texture(packet.mapId(), packet.colors()));
        else texture.update(packet.colors());
    }

    static void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        textures.values().forEach(t -> manager.release(t.id));
        textures.clear();
        places.clear();
        sheets = List.of();
        drawSheets = List.of();
        selected = null;
        tracked = null;
        session = 0;
        revision++;
    }

    static void notice(String message) {
        notice = message;
        noticeUntil = Util.getMillis() + 5000;
    }

    /**
     * requires: render thread; effects: returns current immutable selection or null; throws: none.
     */
    public static Location selected() {
        var value = places.get(selected);
        return value == null ? null : value.value;
    }

    /**
     * requires: render thread; effects: returns current immutable destination or null; throws:
     * none.
     */
    public static Location tracked() {
        var value = places.get(tracked);
        return value == null ? null : value.value;
    }

    /**
     * requires: render thread; effects: returns immutable authorized sheet geometry; throws: none.
     */
    public static List<Sheet> sheets() {
        return sheets;
    }

    /** requires: render thread; effects: returns immutable authorized locations; throws: none. */
    public static List<Location> locations() {
        return places.values().stream().map(p -> p.value).toList();
    }

    /** requires: render thread; effects: reports received sheet images; throws: none. */
    public static int loadedSheetCount() {
        return textures.size();
    }

    static boolean charted(String dimension, double x, double z) {
        for (var sheet : sheets) {
            int pixel = sheet.pixel(x, z);
            var texture = textures.get(sheet.id());
            if (sheet.dimension().equals(dimension)
                    && pixel >= 0
                    && texture != null
                    && (texture.colors[pixel] & 255) / 4 != 0) return true;
        }
        return false;
    }

    /** requires: render thread; effects: opens UI only while a real atlas is held; throws: none. */
    public static void open() {
        var mc = Minecraft.getInstance();
        if (mc.player != null && !AtlasPages.held(mc.player).isEmpty())
            mc.setScreen(new AtlasScreen());
    }

    static void send(
            Payloads.Operation operation,
            Location location,
            String name,
            String icon,
            int color,
            String dimension,
            double x,
            double z) {
        PacketDistributor.sendToServer(
                new Payloads.Action(
                        session,
                        operation,
                        location == null ? "" : location.provider(),
                        location == null ? "" : location.id(),
                        name,
                        icon,
                        color,
                        dimension,
                        x,
                        z));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        while (ClientSetup.OPEN.consumeClick()) {
            if (mc.screen == null) open();
        }
        while (ClientSetup.TRAVEL.consumeClick()) travel = !travel;
        if (mc.player == null && session != 0) clear();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
