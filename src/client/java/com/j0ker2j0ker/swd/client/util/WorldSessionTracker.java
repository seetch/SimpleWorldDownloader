package com.j0ker2j0ker.swd.client.util;

import com.j0ker2j0ker.swd.client.SwdClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;

import java.util.ArrayList;
import java.util.List;

public final class WorldSessionTracker {
    private static final List<WorldTarget> targets = new ArrayList<>();
    private static ClientPacketListener connection;
    private static int currentTarget = -1;
    private static int selectedTarget = -1;

    private WorldSessionTracker() {}

    public static synchronized void ensureConnection(ClientPacketListener listener) {
        if (connection == listener) return;
        targets.clear();
        connection = listener;
        currentTarget = -1;
        selectedTarget = -1;
    }

    public static synchronized boolean isKnownConnection(ClientPacketListener listener) {
        return connection == listener && currentTarget >= 0;
    }

    public static synchronized boolean onLogin(ClientPacketListener listener, ClientboundLoginPacket packet) {
        ensureConnection(listener);

        String fingerprint = fingerprint(packet);
        int targetIndex = findTarget(fingerprint);
        if (targetIndex == currentTarget && currentTarget >= 0) {
            targetIndex = targets.size();
            targets.add(new WorldTarget(nextSlot(serverKey()), fingerprint));
        } else if (targetIndex < 0) {
            targetIndex = targets.size();
            targets.add(new WorldTarget(resolveSlot(fingerprint), fingerprint));
        }

        boolean changed = currentTarget >= 0 && currentTarget != targetIndex;
        currentTarget = targetIndex;
        selectedTarget = targetIndex;
        return changed;
    }

    public static synchronized boolean selectNext() {
        if (targets.size() < 2) return false;
        selectedTarget = (selectedTarget + 1) % targets.size();
        return true;
    }

    public static synchronized int getSelectedNumber() {
        return selectedTarget >= 0 ? targets.get(selectedTarget).slot : 1;
    }

    public static synchronized int getTargetCount() {
        return Math.max(1, targets.size());
    }

    public static synchronized boolean isSelectedCurrent() {
        return selectedTarget < 0 || selectedTarget == currentTarget;
    }

    public static synchronized String applySelectedSuffix(String worldName) {
        int number = getSelectedNumber();
        return number > 1 ? worldName + " - world " + number : worldName;
    }

    public static synchronized void reset() {
        targets.clear();
        connection = null;
        currentTarget = -1;
        selectedTarget = -1;
    }

    private static int findTarget(String fingerprint) {
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).fingerprint.equals(fingerprint)) return i;
        }
        return -1;
    }

    private static int resolveSlot(String fingerprint) {
        if (SwdClient.CONFIG.worldTargetSlots == null) {
            SwdClient.CONFIG.worldTargetSlots = new java.util.HashMap<>();
        }

        String key = serverKey() + "|" + fingerprint;
        Integer existing = SwdClient.CONFIG.worldTargetSlots.get(key);
        if (existing != null && existing > 0) return existing;

        int slot = nextSlot(serverKey());
        SwdClient.CONFIG.worldTargetSlots.put(key, slot);
        SwdClient.CONFIG.save();
        return slot;
    }

    private static int nextSlot(String serverKey) {
        if (SwdClient.CONFIG.worldTargetSlots == null) return 1;
        String prefix = serverKey + "|";
        return SwdClient.CONFIG.worldTargetSlots.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .mapToInt(entry -> entry.getValue() != null ? entry.getValue() : 0)
                .max()
                .orElse(0) + 1;
    }

    private static String serverKey() {
        var server = Minecraft.getInstance().getCurrentServer();
        return server != null ? server.ip.toLowerCase(java.util.Locale.ROOT) : "local";
    }

    private static String fingerprint(ClientboundLoginPacket packet) {
        var spawnInfo = packet.commonPlayerSpawnInfo();
        String levels = packet.levels().stream()
                .map(level -> level.identifier().toString())
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return spawnInfo.seed() + "|" + levels + "|" + spawnInfo.isDebug() + "|" + spawnInfo.isFlat();
    }

    private record WorldTarget(int slot, String fingerprint) {}
}
