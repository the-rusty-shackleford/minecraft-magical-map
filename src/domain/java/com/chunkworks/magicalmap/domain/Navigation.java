/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.domain;

/** Pure navigation geometry; Minecraft yaw 0 faces south, positive 90 west. */
public final class Navigation {
    private Navigation() {}

    /** requires: finite coordinates; effects: returns horizontal distance; throws: none. */
    public static double distance(double x, double z, double targetX, double targetZ) {
        return Math.hypot(targetX - x, targetZ - z);
    }

    /** requires: finite coordinates; effects: returns Minecraft yaw toward target; throws: none. */
    public static double bearing(double x, double z, double targetX, double targetZ) {
        return Math.toDegrees(Math.atan2(-(targetX - x), targetZ - z));
    }

    /** requires: finite yaw; effects: returns one of eight compass headings; throws: none. */
    public static String heading(double yaw) {
        return HEADINGS[Math.floorMod((int) Math.floor(yaw / 45 + .5), 8)];
    }

    private static final String[] HEADINGS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
}
