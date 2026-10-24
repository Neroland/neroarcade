package za.co.neroland.neroarcade.privacy;

import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/**
 * The single place every player-keyed store's backup refresh goes through. Production runs the refresh (Core's
 * {@code SavedDataRecovery.backupNow}) when a live server is present; the erasure tests swap in a recorder so they can
 * prove every persisted store refreshes its backup after an erasure, and read exactly what that backup would hold
 * ({@code content}: the store's own codec output, the bytes {@code backupNow} writes).
 */
public final class BackupHook {

    /** One backup refresh: {@code backup} is the real {@code SavedDataRecovery.backupNow} call. */
    @FunctionalInterface
    public interface Sink {
        void backup(MinecraftServer server, String store, Supplier<CompoundTag> content, Runnable backup);
    }

    /** Runs the refresh; without a live server there is no level to back up to, so nothing happens. */
    public static final Sink DIRECT = (server, store, content, backup) -> {
        if (server != null) {
            backup.run();
        }
    };

    private static volatile Sink current = DIRECT;

    private BackupHook() {
    }

    public static Sink current() {
        return current;
    }

    /** Test seam: {@code null} restores {@link #DIRECT}. */
    public static void set(Sink sink) {
        current = sink == null ? DIRECT : sink;
    }
}
