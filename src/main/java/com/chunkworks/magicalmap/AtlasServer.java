/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import com.chunkworks.magicalmap.api.*;
import com.chunkworks.magicalmap.domain.Sheet;
import com.chunkworks.magicalmap.integration.*;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.*;

import java.util.*;

/**
 * Server-thread atlas sessions. AF: current held atlas -> authorized locations and sent terrain.
 * RI: one session/player; old sessions cannot act; bounded maps/locations; no client authority.
 * Cache lifetime ends on unequip, atlas change, disconnect or server stop. Client snapshots never
 * grant teleport permission. Terrain is vanilla map saved data, not a second exploration database.
 */
public final class AtlasServer {
    private static final Logger LOG = LoggerFactory.getLogger(AtlasServer.class);
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static Map<String, LocationProvider> providers = Map.of();
    private static long nextSession;
    private static final Set<String> FAILED_PROVIDERS = new HashSet<>();

    private AtlasServer() {}

    private static final class Session {
        final long id = ++nextSession;
        final ItemStack atlas;
        final ItemContainerContents contents;
        final List<Sheet> sheets;
        final Map<Integer, byte[]> sent = new HashMap<>();
        Map<Location.Key, Location> locations = Map.of();
        int cursor, sampleCursor;
        long nextAction;

        Session(ItemStack atlas, ServerPlayer player) {
            this.atlas = atlas;
            contents = atlas.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            sheets = AtlasPages.sheets(atlas, player.serverLevel());
        }
    }

    private static volatile MinecraftServer server;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        SESSIONS.clear();
        FAILED_PROVIDERS.clear();
        server = event.getServer();
        var registration = new RegisterLocationProvidersEvent(event.getServer());
        registration.register(new PlayerLocations(event.getServer()));
        registration.register(Landmarks.get(event.getServer()));
        OptionalLocations.register(registration);
        NeoForge.EVENT_BUS.post(registration);
        providers = registration.finish();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        providers = Map.of();
        FAILED_PROVIDERS.clear();
        if (server == event.getServer()) server = null;
    }

    /** requires: none; effects: the providers registered for the running server, empty between servers. */
    public static Map<String, LocationProvider> providers() {
        return providers;
    }

    /** requires: none; effects: the running server, or null between servers. */
    public static MinecraftServer server() {
        return server;
    }

    /**
     * requires: server player; effects: snapshots current identity and permission; throws: none.
     */
    public static Viewer viewer(ServerPlayer player) {
        return new Viewer(
                player.getUUID(),
                player.level().dimension().location().toString(),
                player.hasPermissions(2));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        SESSIONS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (var player : server.getPlayerList().getPlayers()) {
            var atlas = AtlasPages.held(player);
            var session = SESSIONS.get(player.getUUID());
            if (atlas.isEmpty()) {
                if (session != null) {
                    SESSIONS.remove(player.getUUID());
                    PacketDistributor.sendToPlayer(
                            player,
                            new Payloads.State(
                                    ++nextSession, true, List.of(), List.of(), List.of()));
                }
                continue;
            }
            try {
                if (session == null
                        || session.atlas != atlas
                        || !session.contents.equals(
                                atlas.getOrDefault(
                                        DataComponents.CONTAINER, ItemContainerContents.EMPTY))) {
                    int folded = AtlasPages.foldHeld(player, player.serverLevel());
                    if (folded > 0) {
                        // The hand now holds a new stack; the next tick opens its session.
                        notice(
                                player,
                                folded
                                        + (folded == 1 ? " duplicate sheet" : " duplicate sheets")
                                        + " folded: every cell is charted once now.");
                        continue;
                    }
                    session = new Session(atlas, player);
                    SESSIONS.put(player.getUUID(), session);
                    PacketDistributor.sendToPlayer(
                            player,
                            new Payloads.State(
                                    session.id, true, session.sheets, List.of(), List.of()));
                }
                sample(player, session);
                if (server.getTickCount() % 5 == 0) {
                    syncLocations(player, session);
                    syncTerrain(player, session);
                }
            } catch (IllegalArgumentException invalidAtlas) {
                if (SESSIONS.remove(player.getUUID()) != null)
                    notice(
                            player,
                            "This atlas contains invalid sheets. Its items have been preserved.");
            }
        }
    }

    private static void sample(ServerPlayer player, Session session) {
        if (session.sheets.isEmpty()) return;
        // At most one vanilla map update per tick, near its carrier. Remote sheets are only read.
        for (int i = 0; i < session.sheets.size(); i++) {
            var sheet = session.sheets.get(session.sampleCursor);
            session.sampleCursor = (session.sampleCursor + 1) % session.sheets.size();
            if (sheet.locked()
                    || !sheet.dimension().equals(player.level().dimension().location().toString())
                    || !sheet.contains(player.getX(), player.getZ())) continue;
            var data = player.level().getMapData(new MapId(sheet.id()));
            if (data != null) {
                data.tickCarriedBy(player, session.atlas);
                ((MapItem) Items.FILLED_MAP).update(player.level(), player, data);
            }
            return;
        }
    }

    private static void syncTerrain(ServerPlayer player, Session session) {
        // First transmission is progressive, never an unbounded mega-packet; subsequent scans
        // compare two sheets per pass and only send changed 16 KiB images.
        int budget = session.sent.size() < session.sheets.size() ? 8 : 2;
        Sheet nearby =
                session.sheets.stream()
                        .filter(
                                s ->
                                        s.dimension()
                                                        .equals(
                                                                player.level()
                                                                        .dimension()
                                                                        .location()
                                                                        .toString())
                                                && s.contains(player.getX(), player.getZ()))
                        .min(Comparator.comparingInt(Sheet::scale))
                        .orElse(null);
        var visited = new HashSet<Integer>();
        for (int i = 0; i < Math.min(budget, session.sheets.size()); i++) {
            var sheet = i == 0 && nearby != null ? nearby : session.sheets.get(session.cursor);
            if (i != 0 || nearby == null)
                session.cursor = (session.cursor + 1) % session.sheets.size();
            if (!visited.add(sheet.id())) continue;
            var data = player.level().getMapData(new MapId(sheet.id()));
            if (data != null && !Arrays.equals(session.sent.get(sheet.id()), data.colors)) {
                session.sent.put(sheet.id(), data.colors.clone());
                PacketDistributor.sendToPlayer(
                        player, new Payloads.Terrain(session.id, sheet.id(), data.colors));
            }
        }
    }

    /**
     * requires: a registered provider; effects: its snapshot for the viewer when it keeps the
     * contract (at most 256 places, each under its own identity, no key twice), otherwise none,
     * the failure logged once per provider and server so the atlas and the Azimuth bar drop the
     * same provider for the same reason.
     */
    public static List<Location> places(LocationProvider provider, Viewer viewer) {
        try {
            var places = provider.snapshot(viewer);
            if (places.size() > 256)
                throw new IllegalArgumentException("Provider exceeds 256 places");
            var keys = new HashSet<Location.Key>();
            for (var place : places) {
                if (!place.provider().equals(provider.id()))
                    throw new IllegalArgumentException("Provider returned foreign identity");
                if (!keys.add(place.key()))
                    throw new IllegalArgumentException("Duplicate location ID");
            }
            return places;
        } catch (RuntimeException failure) {
            if (FAILED_PROVIDERS.add(provider.id()))
                LOG.error("Atlas provider failed: " + provider.id(), failure);
            return List.of();
        }
    }

    private static void syncLocations(ServerPlayer player, Session session) {
        var current = new LinkedHashMap<Location.Key, Location>();
        var viewer = viewer(player);
        for (var provider : providers.values())
            for (var place : places(provider, viewer)) {
                if (current.size() >= 768) break;
                current.put(place.key(), place);
            }
        var changes =
                current.values().stream()
                        .filter(p -> !p.equals(session.locations.get(p.key())))
                        .toList();
        var removed =
                session.locations.keySet().stream().filter(id -> !current.containsKey(id)).toList();
        if (!changes.isEmpty() || !removed.isEmpty())
            PacketDistributor.sendToPlayer(
                    player, new Payloads.State(session.id, false, List.of(), changes, removed));
        session.locations = Map.copyOf(current);
    }

    /**
     * requires: decoded client request; effects: validates current held atlas/session and applies
     * only authorized action, sends status; throws: none for invalid client input.
     */
    public static void action(ServerPlayer player, Payloads.Action request) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null
                || session.id != request.session()
                || session.atlas != AtlasPages.held(player)
                || !session.contents.equals(
                        session.atlas.getOrDefault(
                                DataComponents.CONTAINER, ItemContainerContents.EMPTY))) {
            notice(player, "Hold your current atlas to use it.");
            return;
        }
        long tick = player.server.getTickCount();
        if (tick < session.nextAction) return;
        session.nextAction = tick + 4;
        try {
            var landmarks = Landmarks.get(player.server);
            var viewer = viewer(player);
            switch (request.operation()) {
                case TELEPORT -> {
                    session.nextAction = tick + 40;
                    teleport(player, request.provider(), request.id());
                }
                case REMOVE -> {
                    if (!request.provider().equals(Landmarks.ID))
                        throw new IllegalArgumentException(
                                "Only personal landmarks can be removed");
                    landmarks.remove(player.getUUID(), request.id());
                    notice(player, "Landmark removed.");
                }
                case EDIT -> {
                    var old =
                            landmarks.snapshot(viewer).stream()
                                    .filter(p -> p.id().equals(request.id()))
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Landmark no longer exists"));
                    landmarks.put(
                            player.getUUID(),
                            new Location(
                                    Landmarks.ID,
                                    old.id(),
                                    old.kind(),
                                    request.name(),
                                    old.dimension(),
                                    old.x(),
                                    old.y(),
                                    old.z(),
                                    old.knownHeight(),
                                    request.icon(),
                                    request.color(),
                                    old.owner(),
                                    false,
                                    ""));
                    notice(player, "Landmark updated.");
                }
                case ADD_HERE, ADD_POINT -> {
                    boolean here = request.operation() == Payloads.Operation.ADD_HERE;
                    String dimension = here ? viewer.dimension() : request.dimension();
                    double x = here ? player.getX() : request.x(),
                            z = here ? player.getZ() : request.z();
                    if (!here
                            && !AtlasPages.charted(session.atlas, player.level(), dimension, x, z))
                        throw new IllegalArgumentException("Choose a charted part of your map");
                    landmarks.put(
                            player.getUUID(),
                            new Location(
                                    Landmarks.ID,
                                    UUID.randomUUID().toString(),
                                    "magicalmap:landmark",
                                    request.name(),
                                    dimension,
                                    x,
                                    here ? player.getY() : 0,
                                    z,
                                    here,
                                    request.icon(),
                                    request.color(),
                                    Optional.of(player.getUUID()),
                                    false,
                                    ""));
                    notice(player, "Landmark saved.");
                }
            }
            syncLocations(player, session);
        } catch (IllegalArgumentException refused) {
            notice(player, refused.getMessage());
        } catch (RuntimeException error) {
            LOG.error("Atlas action failed", error);
            notice(player, "The atlas action could not be completed.");
        }
    }

    /**
     * requires: actual server player; effects: re-resolves an operator destination, loads existing
     * terrain and teleports only onto safe ground; throws: refused permission/stale/unsafe
     * location.
     */
    public static void teleport(ServerPlayer player, String providerId, String id) {
        if (!player.hasPermissions(2))
            throw new IllegalArgumentException("Teleport requires operator permission");
        if (player.isPassenger() || player.isSleeping() || !player.isAlive())
            throw new IllegalArgumentException("Dismount and stand up before teleporting");
        var provider = providers.get(providerId);
        if (provider == null)
            throw new IllegalArgumentException("Location provider is unavailable");
        var target =
                provider.resolve(viewer(player), id)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Destination is unavailable"));
        if (!target.teleportable()
                || !target.provider().equals(providerId)
                || !target.id().equals(id))
            throw new IllegalArgumentException("Invalid destination");
        var level =
                player.server.getLevel(
                        ResourceKey.create(
                                Registries.DIMENSION, ResourceLocation.parse(target.dimension())));
        if (level == null || !SafeArrival.loadDestination(level, target, player.getId()))
            throw new IllegalArgumentException("Destination terrain is unavailable");
        var current =
                provider.resolve(viewer(player), id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Destination changed; try again"));
        if (!current.teleportable()
                || !current.dimension().equals(target.dimension())
                || current.x() != target.x()
                || current.y() != target.y()
                || current.z() != target.z())
            throw new IllegalArgumentException("Destination moved; try again");
        var feet =
                SafeArrival.find(level, player, current)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "No safe arrival point near this destination"));
        player.teleportTo(level, feet.x, feet.y, feet.z, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
        notice(player, "Arrived at " + current.name());
    }

    private static void notice(ServerPlayer player, String message) {
        PacketDistributor.sendToPlayer(
                player, new Payloads.Notice(message == null ? "Atlas action refused" : message));
    }
}
