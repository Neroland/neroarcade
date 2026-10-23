package za.co.neroland.neroarcade.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.bench.CreditResolver;
import za.co.neroland.neroarcade.cabinet.BoardWriter;
import za.co.neroland.neroarcade.data.NeroArcadeData;
import za.co.neroland.neroarcade.network.BoardPagePayload;
import za.co.neroland.neroarcade.privacy.CreditOptIn;
import za.co.neroland.neroarcade.server.ServerHooks;

/**
 * Wiring for the leaderboards and the credit opt-in (003 §6): the {@link BoardWriter} a cabinet's {@code MatchHost}
 * is built with, the retention sweep (on the first server tick after a world loads, then hourly), the erasure
 * registrations, and the read-side page builder used by the marquee, the attract screen and {@code /neroarcade top}.
 */
public final class Boards {

    /** One in-game hour (72 000 ticks = one real hour) between retention sweeps. */
    public static final int PURGE_INTERVAL_TICKS = 72_000;

    /** Erases a player's rows and personal bests from every board. */
    public static final NeroArcadeData.StoreEraser LEADERBOARD_ERASER = new NeroArcadeData.StoreEraser() {
        @Override
        public String name() {
            return "leaderboards";
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            if (server != null) {
                LeaderboardStore.get(server).erase(player);
            }
        }

        @Override
        public void backupNow(MinecraftServer server) {
            LeaderboardStore.get(server).backup(server);
        }
    };

    /** Forgets a player's credit flag. */
    public static final NeroArcadeData.StoreEraser CREDIT_ERASER = new NeroArcadeData.StoreEraser() {
        @Override
        public String name() {
            return "credit";
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            if (server != null) {
                CreditOptIn.get(server).erase(player);
            }
        }

        @Override
        public void backupNow(MinecraftServer server) {
            CreditOptIn.get(server).backup(server);
        }
    };

    private static MinecraftServer sweptFor;
    private static long ticksSinceSweep;
    private static boolean initialised;

    private Boards() {
    }

    /** Common init (step 4, beside the erasure registration). Idempotent. */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        NeroArcadeData.addStore(LEADERBOARD_ERASER);
        NeroArcadeData.addStore(CREDIT_ERASER);
        CreditOptIn.onOptOut((server, player) -> LeaderboardStore.get(server).stripNames(server, player));
        CreditResolver.install((server, author, profileName) -> CreditOptIn.isOn(server, author)
                ? CreditResolver.trim(profileName) : Optional.empty());
        ServerHooks.onTick(Boards::onServerTick);
        ServerHooks.onStopping(server -> {
            sweptFor = null;
            ticksSinceSweep = 0;
        });
    }

    /** The stores this class registers for erasure (for the conformance tests). */
    public static List<NeroArcadeData.StoreEraser> erasers() {
        return List.of(LEADERBOARD_ERASER, CREDIT_ERASER);
    }

    /**
     * The writer a cabinet host is built with. It calls the package-private {@link LeaderboardStore#record}; names
     * come from the credit opt-in at this moment.
     */
    public static BoardWriter writer(MinecraftServer server) {
        return result -> LeaderboardStore.get(server).record(server, result, names(server));
    }

    /** A player's profile name if they opted in to credit (they are online: they just played), else null. */
    static Function<UUID, String> names(MinecraftServer server) {
        return names(uuid -> CreditOptIn.isOn(server, uuid), uuid -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            return player == null ? null : player.getGameProfile().name();
        });
    }

    /** Pure: the profile name (cut to 16) only for an opted-in player, else null. Never a UUID. */
    static Function<UUID, String> names(Predicate<UUID> optedIn, Function<UUID, String> profileName) {
        return uuid -> optedIn.test(uuid) ? CreditResolver.trim(profileName.apply(uuid)).orElse(null) : null;
    }

    /** Retention: the first tick after a world loads, then every {@link #PURGE_INTERVAL_TICKS}. */
    static void onServerTick(MinecraftServer server) {
        if (sweptFor != server) {
            sweptFor = server;
            ticksSinceSweep = PURGE_INTERVAL_TICKS;
        }
        if (++ticksSinceSweep < PURGE_INTERVAL_TICKS) {
            return;
        }
        ticksSinceSweep = 0;
        int removed = LeaderboardStore.get(server).purgeExpired(server, System.currentTimeMillis() / 1000L);
        if (removed > 0) {
            NeroArcadeCommon.LOGGER.debug("[NeroArcade] leaderboard retention removed {} row(s)", removed);
        }
    }

    /**
     * A board as the client may see it: ranks, scores and opted-in names only (empty = "Anonymous"), plus the
     * viewer's own personal best. Pure.
     */
    public static BoardPagePayload page(String lineageRoot, Optional<LeaderboardTable.Board> board, UUID viewer,
            int rows) {
        List<BoardPagePayload.Row> out = new ArrayList<>();
        int myBest = -1;
        if (board.isPresent()) {
            List<LeaderboardTable.Entry> top = board.get().top();
            int rank = 0;
            int lastScore = Integer.MIN_VALUE;
            for (int i = 0; i < top.size() && out.size() < Math.min(rows, BoardPagePayload.MAX_ROWS); i++) {
                LeaderboardTable.Entry e = top.get(i);
                if (e.score() != lastScore) {
                    rank = i + 1;
                    lastScore = e.score();
                }
                out.add(new BoardPagePayload.Row(rank, e.name() == null ? "" : e.name(), e.score()));
            }
            if (viewer != null) {
                myBest = board.get().personalBest().getOrDefault(viewer, -1);
            }
        }
        return new BoardPagePayload(lineageRoot, out, myBest);
    }
}
