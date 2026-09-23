/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.client;

import com.chunkworks.magicalmap.*;
import com.chunkworks.magicalmap.api.Location;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Interactive planning view. Pan/zoom never changes charted terrain or server coordinates. */
public final class AtlasScreen extends Screen {
    private double centerX, centerZ, scale = 2;
    private String dimension, kind = "";
    private AtlasCanvas.Bounds map;
    private EditBox search;
    private AtlasButton dimensionButton,
            kindButton,
            trackButton,
            teleportButton,
            editButton,
            deleteButton;
    private int sidebar, listTop, listBottom, scroll;
    private boolean dragging;
    private boolean fitted;
    private List<AtlasCanvas.Hit> hits = List.of();
    private List<Location> rows = List.of();
    private long rowsRevision = -1;
    private String rowQuery = "", rowDimension = "", rowKind = "";

    public AtlasScreen() {
        super(Component.literal("Magical Atlas"));
        var p = Minecraft.getInstance().player;
        dimension = p == null ? "minecraft:overworld" : p.level().dimension().location().toString();
        if (p != null) {
            centerX = p.getX();
            centerZ = p.getZ();
        }
    }

    @Override
    protected void init() {
        sidebar = width - 164;
        map = new AtlasCanvas.Bounds(24, 57, sidebar - 36, height - 105);
        addRenderableWidget(new AtlasButton(width - 40, 22, 18, 18, "X", this::onClose));
        dimensionButton =
                addRenderableWidget(
                        new AtlasButton(
                                width - 174,
                                22,
                                126,
                                18,
                                AtlasCanvas.dimensionName(dimension),
                                this::nextDimension));
        addRenderableWidget(
                new AtlasButton(
                        24,
                        height - 39,
                        72,
                        19,
                        "Recenter",
                        () -> {
                            if (minecraft.player != null) {
                                centerX = minecraft.player.getX();
                                centerZ = minecraft.player.getZ();
                                dimension =
                                        minecraft.player.level().dimension().location().toString();
                                updateLabels();
                            }
                        }));
        addRenderableWidget(
                new AtlasButton(
                        101, height - 39, 22, 19, "-", () -> scale = Math.min(32, scale * 2)));
        addRenderableWidget(
                new AtlasButton(
                        127, height - 39, 22, 19, "+", () -> scale = Math.max(.25, scale / 2)));
        addRenderableWidget(
                new AtlasButton(
                        sidebar,
                        57,
                        140,
                        19,
                        "Mark current position",
                        () ->
                                minecraft.setScreen(
                                        new LandmarkScreen(this, null, dimension, 0, 0, true))));
        search =
                addRenderableWidget(
                        new EditBox(
                                font,
                                sidebar + 3,
                                84,
                                134,
                                16,
                                Component.literal("Search locations")));
        search.setMaxLength(64);
        search.setHint(Component.literal("Search locations..."));
        kindButton =
                addRenderableWidget(
                        new AtlasButton(sidebar, 105, 140, 18, "All locations", this::nextKind));
        listTop = 128;
        listBottom = Math.max(listTop + 20, height - 157);
        int detail = height - 142;
        trackButton =
                addRenderableWidget(
                        new AtlasButton(
                                sidebar,
                                detail + 65,
                                140,
                                19,
                                "Track destination",
                                () -> {
                                    if (AtlasClient.selected != null)
                                        AtlasClient.tracked =
                                                AtlasClient.selected.equals(AtlasClient.tracked)
                                                        ? null
                                                        : AtlasClient.selected;
                                }));
        teleportButton =
                addRenderableWidget(
                        new AtlasButton(
                                sidebar,
                                detail + 89,
                                140,
                                19,
                                "Teleport (operator)",
                                () -> {
                                    var place = AtlasClient.selected();
                                    if (place != null)
                                        AtlasClient.send(
                                                Payloads.Operation.TELEPORT,
                                                place,
                                                "",
                                                "",
                                                0,
                                                "",
                                                0,
                                                0);
                                }));
        editButton =
                addRenderableWidget(
                        new AtlasButton(
                                sidebar,
                                detail + 89,
                                67,
                                19,
                                "Edit",
                                () -> {
                                    var place = AtlasClient.selected();
                                    if (place != null)
                                        minecraft.setScreen(
                                                new LandmarkScreen(
                                                        this,
                                                        place,
                                                        place.dimension(),
                                                        place.x(),
                                                        place.z(),
                                                        false));
                                }));
        deleteButton =
                addRenderableWidget(
                        new AtlasButton(
                                sidebar + 73,
                                detail + 89,
                                67,
                                19,
                                "Delete",
                                () -> {
                                    var place = AtlasClient.selected();
                                    if (place != null)
                                        AtlasClient.send(
                                                Payloads.Operation.REMOVE,
                                                place,
                                                "",
                                                "",
                                                0,
                                                "",
                                                0,
                                                0);
                                }));
        updateLabels();
        fitSheets();
    }

    private void fitSheets() {
        if (fitted) return;
        var sheets =
                AtlasClient.sheets.stream().filter(s -> s.dimension().equals(dimension)).toList();
        if (sheets.isEmpty()) return;
        int left = sheets.stream().mapToInt(s -> s.left()).min().orElse(0),
                top = sheets.stream().mapToInt(s -> s.top()).min().orElse(0);
        int right = sheets.stream().mapToInt(s -> s.left() + 128 * s.step()).max().orElse(128),
                bottom = sheets.stream().mapToInt(s -> s.top() + 128 * s.step()).max().orElse(128);
        centerX = (left + right) / 2.0;
        centerZ = (top + bottom) / 2.0;
        scale =
                Math.max(
                        .25,
                        Math.min(
                                32,
                                Math.max(
                                        (right - left) / (double) (map.width() - 24),
                                        (bottom - top) / (double) (map.height() - 24))));
        fitted = true;
    }

    private void nextDimension() {
        var dimensions = new TreeSet<String>();
        dimensions.add(dimension);
        AtlasClient.sheets.forEach(s -> dimensions.add(s.dimension()));
        AtlasClient.places.values().forEach(p -> dimensions.add(p.value.dimension()));
        var choices = new ArrayList<>(dimensions);
        dimension = choices.get((choices.indexOf(dimension) + 1) % choices.size());
        var sheet =
                AtlasClient.sheets.stream()
                        .filter(s -> s.dimension().equals(dimension))
                        .findFirst();
        if (sheet.isPresent()) {
            centerX = sheet.get().centerX();
            centerZ = sheet.get().centerZ();
        } else
            AtlasClient.places.values().stream()
                    .filter(p -> p.value.dimension().equals(dimension))
                    .findFirst()
                    .ifPresent(
                            p -> {
                                centerX = p.value.x();
                                centerZ = p.value.z();
                            });
        scroll = 0;
        updateLabels();
    }

    private void nextKind() {
        var kinds = new TreeSet<String>();
        AtlasClient.places.values().forEach(p -> kinds.add(p.value.kind()));
        var choices = new ArrayList<String>();
        choices.add("");
        choices.addAll(kinds);
        kind = choices.get((choices.indexOf(kind) + 1) % choices.size());
        scroll = 0;
        updateLabels();
    }

    private void updateLabels() {
        dimensionButton.setMessage(Component.literal(AtlasCanvas.dimensionName(dimension)));
        kindButton.setMessage(
                Component.literal(
                        kind.isEmpty() ? "All locations" : AtlasCanvas.dimensionName(kind)));
    }

    @Override
    public void tick() {
        if (minecraft.player == null || AtlasPages.held(minecraft.player).isEmpty()) onClose();
        else fitSheets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Screen.render calls this before widgets. Our parchment and dimming are already painted;
    // vanilla's default implementation would blur that artwork, not just the world behind it.
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x990c1714);
        AtlasCanvas.frame(g, 12, 12, width - 24, height - 24);
        g.fill(19, 19, width - 19, 47, 0xff304a42);
        g.drawString(font, "MAGICAL ATLAS", 27, 25, 0xfff1e3b7, false);
        g.drawString(
                font,
                AtlasClient.sheets.size() + " sheets  /  Chart your journey",
                27,
                37,
                0xffb5c6a7,
                false);
        hits = AtlasCanvas.draw(g, map, dimension, centerX, centerZ, scale, false);
        if (AtlasClient.sheets.isEmpty()) {
            g.drawCenteredString(
                    font,
                    "An unwritten journey",
                    map.x() + map.width() / 2,
                    map.y() + map.height() / 2 - 12,
                    AtlasCanvas.INK);
            g.drawCenteredString(
                    font,
                    "Bind a map at a cartography table",
                    map.x() + map.width() / 2,
                    map.y() + map.height() / 2 + 2,
                    AtlasCanvas.MUTED);
        }
        int ruler = Math.max(1, (int) Math.round(scale * 40));
        g.hLine(map.x() + 10, map.x() + 50, map.y() + map.height() - 14, AtlasCanvas.INK);
        g.vLine(
                map.x() + 10,
                map.y() + map.height() - 17,
                map.y() + map.height() - 11,
                AtlasCanvas.INK);
        g.vLine(
                map.x() + 50,
                map.y() + map.height() - 17,
                map.y() + map.height() - 11,
                AtlasCanvas.INK);
        g.drawString(
                font,
                ruler + " blocks",
                map.x() + 56,
                map.y() + map.height() - 17,
                AtlasCanvas.INK,
                false);
        g.drawString(
                font,
                "Drag to pan  /  Scroll to zoom  /  Right-click to mark",
                157,
                height - 33,
                AtlasCanvas.MUTED,
                false);
        renderRows(g, mouseX, mouseY);
        renderDetails(g);
        if (Util.getMillis() < AtlasClient.noticeUntil)
            g.drawString(
                    font,
                    font.plainSubstrByWidth(AtlasClient.notice, width - 54),
                    27,
                    49,
                    AtlasCanvas.INK,
                    false);
        super.render(g, mouseX, mouseY, delta);
        if (map.contains(mouseX, mouseY))
            for (var hit : hits)
                if (Math.abs(hit.x() - mouseX) < 10 && Math.abs(hit.y() - mouseY) < 10) {
                    var location = AtlasClient.places.get(hit.ids().getFirst());
                    if (location != null)
                        g.renderTooltip(
                                font,
                                Component.literal(
                                        location.value.name()
                                                + (hit.ids().size() > 1
                                                        ? " (+" + (hit.ids().size() - 1) + ")"
                                                        : "")),
                                mouseX,
                                mouseY);
                    break;
                }
    }

    private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
        String query = search.getValue().toLowerCase(Locale.ROOT);
        if (rowsRevision != AtlasClient.revision
                || !query.equals(rowQuery)
                || !dimension.equals(rowDimension)
                || !kind.equals(rowKind)) {
            rows =
                    AtlasClient.places.values().stream()
                            .map(p -> p.value)
                            .filter(
                                    p ->
                                            p.dimension().equals(dimension)
                                                    && (kind.isEmpty() || kind.equals(p.kind()))
                                                    && p.name()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains(query))
                            .sorted(
                                    Comparator.comparing(
                                            Location::name, String.CASE_INSENSITIVE_ORDER))
                            .toList();
            rowsRevision = AtlasClient.revision;
            rowQuery = query;
            rowDimension = dimension;
            rowKind = kind;
        }
        int count = Math.max(1, (listBottom - listTop) / 23);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - count)));
        g.fill(sidebar, listTop - 2, sidebar + 140, listBottom, 0xffc4b585);
        for (int index = scroll; index < Math.min(rows.size(), scroll + count); index++) {
            var p = rows.get(index);
            int y = listTop + (index - scroll) * 23;
            boolean selected = p.key().equals(AtlasClient.selected),
                    hover =
                            mouseX >= sidebar
                                    && mouseX < sidebar + 140
                                    && mouseY >= y
                                    && mouseY < y + 22;
            if (selected || hover)
                g.fill(sidebar, y - 1, sidebar + 140, y + 21, selected ? 0xffe7d6a2 : 0xffd9c994);
            AtlasCanvas.icon(g, p, sidebar + 4, y + 3, 12, selected);
            g.drawString(
                    font,
                    font.plainSubstrByWidth(p.name(), 113),
                    sidebar + 23,
                    y + 2,
                    AtlasCanvas.INK,
                    false);
            g.drawString(
                    font,
                    font.plainSubstrByWidth(AtlasCanvas.distance(p), 113),
                    sidebar + 23,
                    y + 12,
                    AtlasCanvas.MUTED,
                    false);
        }
        if (rows.isEmpty())
            g.drawString(
                    font, "No locations here", sidebar + 8, listTop + 8, AtlasCanvas.MUTED, false);
        if (rows.size() > count)
            g.drawString(
                    font,
                    (scroll + 1)
                            + "-"
                            + Math.min(rows.size(), scroll + count)
                            + " / "
                            + rows.size(),
                    sidebar + 74,
                    listBottom - 8,
                    AtlasCanvas.MUTED,
                    false);
    }

    private void renderDetails(GuiGraphics g) {
        int y = height - 142;
        var place = AtlasClient.selected();
        g.hLine(sidebar, sidebar + 139, y - 7, 0xffa28f61);
        trackButton.visible = place != null;
        teleportButton.visible = place != null && place.teleportable();
        editButton.visible =
                deleteButton.visible = place != null && place.provider().equals(Landmarks.ID);
        if (place == null) {
            g.drawString(font, "Choose a destination", sidebar, y, AtlasCanvas.INK, false);
            g.drawWordWrap(
                    font,
                    Component.literal("Select an icon or a location in the list."),
                    sidebar,
                    y + 17,
                    137,
                    AtlasCanvas.MUTED);
            return;
        }
        g.drawString(
                font,
                font.plainSubstrByWidth(place.name(), 140),
                sidebar,
                y,
                AtlasCanvas.INK,
                false);
        g.drawString(font, AtlasCanvas.distance(place), sidebar, y + 13, AtlasCanvas.MUTED, false);
        String coords =
                (int) Math.floor(place.x())
                        + ", "
                        + (place.knownHeight() ? (int) Math.floor(place.y()) + ", " : "")
                        + (int) Math.floor(place.z());
        g.drawString(
                font,
                font.plainSubstrByWidth(coords, 140),
                sidebar,
                y + 26,
                AtlasCanvas.MUTED,
                false);
        g.drawString(
                font,
                font.plainSubstrByWidth(
                        place.status().isEmpty()
                                ? AtlasCanvas.dimensionName(place.dimension())
                                : place.status(),
                        140),
                sidebar,
                y + 40,
                AtlasCanvas.MUTED,
                false);
        trackButton.setMessage(
                Component.literal(
                        place.key().equals(AtlasClient.tracked)
                                ? "Stop tracking"
                                : "Track destination"));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (map.contains(x, y)) {
            if (button == 1) {
                double wx = centerX + (x - map.x() - map.width() / 2.0) * scale,
                        wz = centerZ + (y - map.y() - map.height() / 2.0) * scale;
                if (AtlasClient.charted(dimension, wx, wz))
                    minecraft.setScreen(new LandmarkScreen(this, null, dimension, wx, wz, false));
                else AtlasClient.notice("Explore this area with a map before marking it.");
                return true;
            }
            if (button == 0) {
                for (var hit : hits)
                    if (Math.abs(hit.x() - x) < 10 && Math.abs(hit.y() - y) < 10) {
                        AtlasClient.selected = com.chunkworks.magicalmap.domain.Selection.next(hit.ids(), AtlasClient.selected);
                        return true;
                    }
                dragging = true;
                return true;
            }
        }
        if (button == 0 && x >= sidebar && x < sidebar + 140 && y >= listTop && y < listBottom) {
            int index = scroll + (int) (y - listTop) / 23;
            if (index >= 0 && index < rows.size()) {
                AtlasClient.selected = rows.get(index).key();
                centerX = rows.get(index).x();
                centerZ = rows.get(index).z();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) {
            centerX -= dx * scale;
            centerZ -= dy * scale;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        dragging = false;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (map.contains(x, y)) {
            double wx = centerX + (x - map.x() - map.width() / 2.0) * scale,
                    wz = centerZ + (y - map.y() - map.height() / 2.0) * scale;
            scale = Math.max(.25, Math.min(32, scale * Math.pow(2, -vertical / 2)));
            centerX = wx - (x - map.x() - map.width() / 2.0) * scale;
            centerZ = wz - (y - map.y() - map.height() / 2.0) * scale;
            return true;
        }
        if (x >= sidebar) {
            int visible = Math.max(1, (listBottom - listTop) / 23);
            scroll =
                    Math.max(
                            0,
                            Math.min(
                                    Math.max(0, rows.size() - visible),
                                    scroll - (int) Math.signum(vertical)));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
}
