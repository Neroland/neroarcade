package za.co.neroland.neroarcade.privacy;

import java.util.function.Function;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * How an eraser finds its {@code SavedData} store. Production: the live store of {@code server} (none without a
 * server). Tests: a store instance swapped in with {@link #override}, so Core's {@code ErasureConformance} harness and
 * the end-to-end erasure test drive the real erasers against real store objects in a plain JVM.
 *
 * @param <T> the store type
 */
public final class StoreAccess<T> {

    private final Function<MinecraftServer, T> live;
    private volatile @Nullable T override;

    public StoreAccess(Function<MinecraftServer, T> live) {
        this.live = live;
    }

    /** The store an eraser works on: the override, else the live store, else null (no server). */
    public @Nullable T resolve(@Nullable MinecraftServer server) {
        T forced = override;
        if (forced != null) {
            return forced;
        }
        return server == null ? null : live.apply(server);
    }

    /** Test seam: {@code null} restores the live lookup. */
    public void override(@Nullable T store) {
        this.override = store;
    }
}
