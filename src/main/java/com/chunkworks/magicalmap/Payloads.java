/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap;

import com.chunkworks.magicalmap.api.Location;
import com.chunkworks.magicalmap.domain.Sheet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.*;
import java.util.function.Consumer;

/** Protocol 1. Bounded, per-held-atlas sessions. Client never supplies teleport coordinates. */
public final class Payloads {
    public static Runnable openAtlas = () -> {};
    public static Consumer<State> receiveState = unused -> {};
    public static Consumer<Terrain> receiveTerrain = unused -> {};
    public static Consumer<Notice> receiveNotice = unused -> {};

    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(MagicalMap.ID, name));
    }

    public record State(
            long session,
            boolean reset,
            List<Sheet> sheets,
            List<Location> upserts,
            List<Location.Key> removed)
            implements CustomPacketPayload {
        public State {
            sheets = List.copyOf(sheets);
            upserts = List.copyOf(upserts);
            removed = List.copyOf(removed);
        }

        public static final Type<State> TYPE = Payloads.type("state");
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeLong(p.session);
                            b.writeBoolean(p.reset);
                            b.writeVarInt(p.sheets.size());
                            for (var s : p.sheets) {
                                b.writeVarInt(s.id());
                                b.writeUtf(s.dimension(), 160);
                                b.writeInt(s.centerX());
                                b.writeInt(s.centerZ());
                                b.writeByte(s.scale());
                                b.writeBoolean(s.locked());
                            }
                            b.writeVarInt(p.upserts.size());
                            for (var l : p.upserts) writeLocation(b, l);
                            b.writeVarInt(p.removed.size());
                            for (var k : p.removed) {
                                b.writeUtf(k.provider(), 160);
                                b.writeUtf(k.id(), 160);
                            }
                        },
                        b -> {
                            long session = b.readLong();
                            boolean reset = b.readBoolean();
                            var sheets = new ArrayList<Sheet>();
                            int count = count(b, 64);
                            for (int i = 0; i < count; i++)
                                sheets.add(
                                        new Sheet(
                                                b.readVarInt(),
                                                b.readUtf(160),
                                                b.readInt(),
                                                b.readInt(),
                                                b.readUnsignedByte(),
                                                b.readBoolean()));
                            var locations = new ArrayList<Location>();
                            count = count(b, 768);
                            for (int i = 0; i < count; i++) locations.add(readLocation(b));
                            var removed = new ArrayList<Location.Key>();
                            count = count(b, 768);
                            for (int i = 0; i < count; i++)
                                removed.add(new Location.Key(b.readUtf(160), b.readUtf(160)));
                            return new State(session, reset, sheets, locations, removed);
                        });

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    public record Terrain(long session, int mapId, byte[] colors) implements CustomPacketPayload {
        public Terrain {
            if (colors.length != 16384) throw new IllegalArgumentException("Invalid map image");
            colors = colors.clone();
        }

        @Override
        public byte[] colors() {
            return colors.clone();
        }

        public static final Type<Terrain> TYPE = Payloads.type("terrain");
        public static final StreamCodec<RegistryFriendlyByteBuf, Terrain> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeLong(p.session);
                            b.writeVarInt(p.mapId);
                            b.writeByteArray(p.colors);
                        },
                        b -> new Terrain(b.readLong(), b.readVarInt(), b.readByteArray(16384)));

        @Override
        public Type<Terrain> type() {
            return TYPE;
        }
    }

    public enum Operation {
        ADD_HERE,
        ADD_POINT,
        EDIT,
        REMOVE,
        TELEPORT
    }

    public record Action(
            long session,
            Operation operation,
            String provider,
            String id,
            String name,
            String icon,
            int color,
            String dimension,
            double x,
            double z)
            implements CustomPacketPayload {
        public static final Type<Action> TYPE = Payloads.type("action");
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeLong(p.session);
                            b.writeEnum(p.operation);
                            b.writeUtf(p.provider, 160);
                            b.writeUtf(p.id, 160);
                            b.writeUtf(p.name, 64);
                            b.writeUtf(p.icon, 160);
                            b.writeInt(p.color);
                            b.writeUtf(p.dimension, 160);
                            b.writeDouble(p.x);
                            b.writeDouble(p.z);
                        },
                        b ->
                                new Action(
                                        b.readLong(),
                                        b.readEnum(Operation.class),
                                        b.readUtf(160),
                                        b.readUtf(160),
                                        b.readUtf(64),
                                        b.readUtf(160),
                                        b.readInt(),
                                        b.readUtf(160),
                                        b.readDouble(),
                                        b.readDouble()));

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }

    public record Notice(String message) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = Payloads.type("notice");
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC =
                StreamCodec.of(
                        (b, p) -> b.writeUtf(p.message, 256), b -> new Notice(b.readUtf(256)));

        @Override
        public Type<Notice> type() {
            return TYPE;
        }
    }

    private static int count(RegistryFriendlyByteBuf b, int max) {
        int size = b.readVarInt();
        if (size < 0 || size > max) throw new IllegalArgumentException("Oversized atlas packet");
        return size;
    }

    private static void writeLocation(RegistryFriendlyByteBuf b, Location l) {
        b.writeUtf(l.provider(), 160);
        b.writeUtf(l.id(), 160);
        b.writeUtf(l.kind(), 160);
        b.writeUtf(l.name(), 64);
        b.writeUtf(l.dimension(), 160);
        b.writeDouble(l.x());
        b.writeDouble(l.y());
        b.writeDouble(l.z());
        b.writeBoolean(l.knownHeight());
        b.writeUtf(l.icon(), 160);
        b.writeInt(l.color());
        b.writeBoolean(l.owner().isPresent());
        l.owner().ifPresent(b::writeUUID);
        b.writeBoolean(l.teleportable());
        b.writeUtf(l.status(), 80);
    }

    private static Location readLocation(RegistryFriendlyByteBuf b) {
        return new Location(
                b.readUtf(160),
                b.readUtf(160),
                b.readUtf(160),
                b.readUtf(64),
                b.readUtf(160),
                b.readDouble(),
                b.readDouble(),
                b.readDouble(),
                b.readBoolean(),
                b.readUtf(160),
                b.readInt(),
                b.readBoolean() ? Optional.of(b.readUUID()) : Optional.empty(),
                b.readBoolean(),
                b.readUtf(80));
    }

    /**
     * requires: mod registration event; effects: registers protocol and sided handlers; throws:
     * registration errors.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(State.TYPE, State.CODEC, (p, c) -> receiveState.accept(p));
        registrar.playToClient(Terrain.TYPE, Terrain.CODEC, (p, c) -> receiveTerrain.accept(p));
        registrar.playToClient(Notice.TYPE, Notice.CODEC, (p, c) -> receiveNotice.accept(p));
        registrar.playToServer(
                Action.TYPE,
                Action.CODEC,
                (p, c) -> AtlasServer.action((ServerPlayer) c.player(), p));
    }
}
