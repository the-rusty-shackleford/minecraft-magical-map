/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.api;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

import java.util.*;

/**
 * Fired on NeoForge.EVENT_BUS once per server start, after built-ins register. Integrations
 * construct their server adapters here. AF: mutable registration window; RI: unique provider IDs,
 * registration closed after dispatch. All use is confined to the server thread.
 */
public final class RegisterLocationProvidersEvent extends Event {
    private final MinecraftServer server;
    private final Map<String, LocationProvider> providers = new LinkedHashMap<>();
    private boolean closed;

    public RegisterLocationProvidersEvent(MinecraftServer server) {
        this.server = Objects.requireNonNull(server);
    }

    /** requires: none; effects: returns this server, never a global singleton; throws: none. */
    public MinecraftServer server() {
        return server;
    }

    /**
     * requires: event still dispatching; effects: registers provider; throws: duplicate/invalid ID,
     * closed registration.
     */
    public void register(LocationProvider provider) {
        if (closed) throw new IllegalStateException("Registration has closed");
        Location.identifier(provider.id());
        if (providers.putIfAbsent(provider.id(), provider) != null)
            throw new IllegalArgumentException("Duplicate provider: " + provider.id());
    }

    /**
     * requires: event dispatched; effects: closes registration and returns immutable providers;
     * throws: none.
     */
    public Map<String, LocationProvider> finish() {
        closed = true;
        return Map.copyOf(providers);
    }
}
