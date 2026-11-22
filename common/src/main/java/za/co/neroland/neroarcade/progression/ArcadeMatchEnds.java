package za.co.neroland.neroarcade.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.cabinet.MatchListener;
import za.co.neroland.neroarcade.compat.Seams;
import za.co.neroland.neroarcade.compat.tournament.Tournaments;
import za.co.neroland.neroarcade.exchange.Exchange;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.privacy.Tombstones;
import za.co.neroland.neroarcade.registry.ModStats;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * The {@link MatchListener} a cabinet host gets (003 §6.1, in order after the leaderboard write): the
 * {@code match_won} / {@code record_set} threshold channels (scope = the lineage root, never a player), the
 * {@code first_versus_win} gate, the Exchange play count (the first match of each insertion), the tournament
 * fan-out and the morale seam. A hot-seat match is never credited: it only counts a play and tells the morale seam.
 */
public final class ArcadeMatchEnds {

    /** A late subscriber to finished matches (the NeroLink module, 007 §4). Server thread; must not throw. */
    @FunctionalInterface
    public interface EndHook {
        void onEnd(ServerLevel level, MatchListener.MatchEnd end);
    }

    private static final java.util.List<EndHook> HOOKS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ArcadeMatchEnds() {
    }

    /** Adds a hook run after everything else for each finished match. Adding the same instance twice is ignored. */
    public static void addHook(EndHook hook) {
        if (hook != null && !HOOKS.contains(hook)) {
            HOOKS.add(hook);
        }
    }

    /** Test teardown only. */
    public static boolean removeHook(EndHook hook) {
        return HOOKS.remove(hook);
    }

    public static MatchListener listener(ServerLevel level) {
        return end -> handle(level.getServer(), level, end);
    }

    static void handle(MinecraftServer server, ServerLevel level, MatchListener.MatchEnd end) {
        BusMatchResult result = end.result();
        if (!end.hotSeat()) {
            if (end.outcome().won()) {
                ArcadeMilestones.matchWon(result.cartridgeId(), end.outcome().wins());
                for (UUID winner : winners(result)) {
                    awardWin(server, winner);
                }
            }
            if (end.outcome().recordSet()) {
                ArcadeMilestones.recordSet(result.cartridgeId(), end.outcome().records());
                recordHolder(result, end.outcome().enteredTopMask())
                        .ifPresent(holder -> ArcadeMilestones.firstRecord(server, holder));
            }
            versusWinner(end).ifPresent(winner -> ArcadeMilestones.firstVersusWin(server, winner));
            Tournaments.publish(result);
        }
        if (end.firstPlay()) {
            Exchange.countPlay(server, result.cartridgeId());
        }
        try {
            Seams.morale().onMatchPlayed(level, end.hostPos());
        } catch (RuntimeException | LinkageError e) {
            Seams.failed("morale", e);
        }
        for (EndHook hook : HOOKS) {
            try {
                hook.onEnd(level, end);
            } catch (RuntimeException | LinkageError e) {
                za.co.neroland.neroarcade.NeroArcadeCommon.LOGGER.warn("[NeroArcade] match-end hook failed ({})",
                        e.getClass().getName());
            }
        }
    }

    /**
     * The player who earned {@code first_versus_win}: a WIN on a game whose multiplayer chip plays competitively
     * against other seats (the versus capability, {@link ModuleFlag#RUNTIME_OPPONENT}), with at least two human seats
     * (so never against the runtime opponent) and exactly one non-dropped seat ranked first. Never hot-seat. Pure.
     */
    public static Optional<UUID> versusWinner(MatchListener.MatchEnd end) {
        BusMatchResult result = end.result();
        if (end.hotSeat() || result.outcome() != MatchResult.Outcome.WIN || result.seats().size() < 2
                || !end.game().flags(ModuleType.MULTIPLAYER).contains(ModuleFlag.RUNTIME_OPPONENT)) {
            return Optional.empty();
        }
        UUID winner = null;
        for (BusMatchResult.SeatResult seat : result.seats()) {
            if (seat.rank() == 1) {
                if (winner != null || seat.dropped()) {
                    return Optional.empty();
                }
                winner = seat.uuid();
            }
        }
        return Optional.ofNullable(winner);
    }

    /**
     * The seats credited with a win: on a {@code WIN} outcome, every non-dropped human seat ranked first (a solo win
     * is its one seat; tied co-op winners all count). Empty otherwise. Pure.
     */
    public static List<UUID> winners(BusMatchResult result) {
        List<UUID> out = new ArrayList<>();
        if (result.outcome() != MatchResult.Outcome.WIN) {
            return out;
        }
        for (BusMatchResult.SeatResult seat : result.seats()) {
            if (seat.rank() == 1 && !seat.dropped() && !out.contains(seat.uuid())) {
                out.add(seat.uuid());
            }
        }
        return out;
    }

    /**
     * Who set the board's new top score: among the seats that entered the top window this match
     * ({@code enteredTopMask}, a bit per seat index), the one with the highest score. Empty when two seats share
     * that score, so a tie never credits the wrong player. Pure.
     */
    public static Optional<UUID> recordHolder(BusMatchResult result, int enteredTopMask) {
        UUID holder = null;
        int best = Integer.MIN_VALUE;
        boolean tied = false;
        for (BusMatchResult.SeatResult seat : result.seats()) {
            if (seat.dropped() || seat.seat() < 0 || seat.seat() > 30 || (enteredTopMask & (1 << seat.seat())) == 0) {
                continue;
            }
            if (seat.score() > best) {
                best = seat.score();
                holder = seat.uuid();
                tied = false;
            } else if (seat.score() == best && !seat.uuid().equals(holder)) {
                tied = true;
            }
        }
        return tied ? Optional.empty() : Optional.ofNullable(holder);
    }

    /** One more credited win on the winner's vanilla {@code neroarcade:matches_won} stat, if they are online. */
    private static void awardWin(MinecraftServer server, UUID winner) {
        if (server == null || winner == null || Tombstones.isTombstone(winner)) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(winner);
        if (player != null) {
            player.awardStat(ModStats.MATCHES_WON.get());
        }
    }
}
