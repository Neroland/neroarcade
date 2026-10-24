package za.co.neroland.neroarcade.bench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroarcade.data.NeroArcadeData;

/**
 * The Game Benches loaded on the server right now, so an erasure can reach the one piece of player data a bench
 * keeps in its block NBT: the author (and credit) of the cartridge last LOADed into its draft (review 005a, 003b P2).
 * A bench joins on its first server tick and leaves on unload or removal; the set holds weak references, so a bench
 * the level dropped without telling us still goes.
 *
 * <p><b>Documented limit:</b> a bench in an <b>unloaded</b> chunk is not reached. Its draft keeps the author UUID
 * until the next LOAD/SAVE at that bench replaces it, exactly like a cartridge item in a chest (PRIVACY.md). The
 * UUID is never shown or sent: the bench preview and update tag strip it.
 */
public final class LoadedBenches {

    /** What the eraser needs from a bench (the block entity, or a test stand-in). */
    public interface AuthorHolder {
        /** Clears {@code player} as the draft's author/credit. @return true if anything changed */
        boolean eraseAuthor(UUID player);

        boolean holdsAuthor(UUID player);

        /**
         * Credit opt-out (compliance review F5): if the draft's origin names {@code player} as author, its credit
         * goes; the author stays (opt-out is not erasure). @return true if anything changed
         */
        boolean stripCredit(UUID player);
    }

    /** Longest an off-thread erasure waits for the server thread to walk the benches before failing. */
    static final long OFF_THREAD_WAIT_SECONDS = 30;

    private static final Set<AuthorHolder> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Erasure, benches: every loaded bench whose draft names the player loses that author and credit. Block NBT
     * is saved with its chunk, so there is no backup to refresh. Off the server thread the walk is handed to it and
     * awaited (compliance review F7), so "complete" is only reported once it is true; the wait never happens on the
     * server thread itself (no deadlock), and is bounded so a stopped server fails the erasure as INCOMPLETE rather
     * than hanging.
     */
    public static final NeroArcadeData.StoreEraser ERASER = new NeroArcadeData.StoreEraser() {
        @Override
        public String name() {
            return "benches";
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            if (player == null) {
                return;
            }
            if (server != null && !server.isSameThread()) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    eraseAuthor(player);
                }, server).orTimeout(OFF_THREAD_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS).join();
            } else {
                eraseAuthor(player);
            }
        }

        @Override
        public void backupNow(MinecraftServer server) {
            // block NBT: saved with the chunk, no SavedData backup
        }
    };

    private LoadedBenches() {
    }

    public static synchronized void add(AuthorHolder bench) {
        LOADED.add(bench);
    }

    public static synchronized void remove(AuthorHolder bench) {
        LOADED.remove(bench);
    }

    public static synchronized void clear() {
        LOADED.clear();
    }

    public static synchronized int size() {
        return LOADED.size();
    }

    private static synchronized List<AuthorHolder> snapshot() {
        return new ArrayList<>(LOADED);
    }

    /** @return benches changed */
    public static int eraseAuthor(UUID player) {
        int changed = 0;
        for (AuthorHolder bench : snapshot()) {
            if (bench.eraseAuthor(player)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Credit opt-out: every loaded bench whose draft names {@code player} as author drops the credit. Registered
     * with {@code CreditOptIn.onOptOut} by {@code ArcadePrivacy.init()}; server thread (the opt-out command).
     *
     * @return benches changed
     */
    public static int stripCredit(UUID player) {
        if (player == null) {
            return 0;
        }
        int changed = 0;
        for (AuthorHolder bench : snapshot()) {
            if (bench.stripCredit(player)) {
                changed++;
            }
        }
        return changed;
    }

    /** Erasure-conformance probe: does any loaded bench still name {@code player}? */
    public static boolean holdsAuthor(UUID player) {
        for (AuthorHolder bench : snapshot()) {
            if (bench.holdsAuthor(player)) {
                return true;
            }
        }
        return false;
    }
}
