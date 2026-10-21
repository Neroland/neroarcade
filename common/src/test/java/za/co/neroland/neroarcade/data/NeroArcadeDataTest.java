package za.co.neroland.neroarcade.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;

/**
 * The erasure seam: registered with Core exactly once, walks every added store, keeps going past a
 * failing store and then reports the request as incomplete. Plain JVM, no game bootstrap (the server
 * is {@code null}, so no backup refresh is attempted).
 */
class NeroArcadeDataTest {

    @Test
    void registersWithCoreExactlyOnce() {
        int before = PlayerDataErasure.registeredCount();
        boolean wasRegistered = NeroArcadeData.isRegistered();
        NeroArcadeData.init();
        NeroArcadeData.init();
        assertTrue(NeroArcadeData.isRegistered());
        assertEquals(wasRegistered ? before : before + 1, PlayerDataErasure.registeredCount());
    }

    @Test
    void walksEveryStoreAndReportsFailures() {
        List<String> erased = new ArrayList<>();
        NeroArcadeData.StoreEraser failing = store("failing", erased, true);
        NeroArcadeData.StoreEraser healthy = store("healthy", erased, false);
        NeroArcadeData.addStore(failing);
        NeroArcadeData.addStore(healthy);
        NeroArcadeData.addStore(healthy); // duplicate ignored
        try {
            assertEquals(2, NeroArcadeData.storeCount());
            UUID player = UUID.randomUUID();
            assertThrows(IllegalStateException.class, () -> NeroArcadeData.eraser().erase(null, player));
            assertEquals(List.of("failing", "healthy"), erased);
        } finally {
            NeroArcadeData.removeStore(failing);
            NeroArcadeData.removeStore(healthy);
        }
        assertEquals(0, NeroArcadeData.storeCount());
    }

    private static NeroArcadeData.StoreEraser store(String name, List<String> log, boolean fail) {
        return new NeroArcadeData.StoreEraser() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                log.add(name);
                if (fail) {
                    throw new IllegalStateException("simulated store failure");
                }
            }

            @Override
            public void backupNow(MinecraftServer server) {
                throw new AssertionError("no backup refresh without a live server");
            }
        };
    }
}
