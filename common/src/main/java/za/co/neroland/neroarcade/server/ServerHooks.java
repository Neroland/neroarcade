package za.co.neroland.neroarcade.server;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/**
 * Server lifecycle seam (the NeroSecurity shape): each loader calls the {@code fire*} methods from its own events;
 * common code registers listeners with the {@code on*} methods. Events flow loader → common, so no ServiceLoader
 * is involved. Every listener call is isolated: one that throws is logged by class name once and the next still
 * runs. Server thread only.
 */
public final class ServerHooks {

    @FunctionalInterface
    public interface Stopping {
        void on(MinecraftServer server);
    }

    @FunctionalInterface
    public interface PlayerLeave {
        void on(ServerPlayer player);
    }

    private static final List<Stopping> STOPPING = new CopyOnWriteArrayList<>();
    private static final List<PlayerLeave> LEAVE = new CopyOnWriteArrayList<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ServerHooks() {
    }

    public static void onStopping(Stopping listener) {
        STOPPING.add(listener);
    }

    public static void onPlayerLeave(PlayerLeave listener) {
        LEAVE.add(listener);
    }

    public static void fireStopping(MinecraftServer server) {
        for (Stopping listener : STOPPING) {
            guard(listener, "stopping", () -> listener.on(server));
        }
    }

    public static void firePlayerLeave(ServerPlayer player) {
        for (PlayerLeave listener : LEAVE) {
            guard(listener, "leave", () -> listener.on(player));
        }
    }

    private static void guard(Object listener, String hook, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException | LinkageError e) {
            if (WARNED.add(listener.getClass().getName())) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] server {} listener {} failed ({}); other listeners still ran.",
                        hook, listener.getClass().getName(), e.getClass().getName());
            }
        }
    }
}
