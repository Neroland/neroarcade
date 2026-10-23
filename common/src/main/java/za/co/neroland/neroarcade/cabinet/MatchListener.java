package za.co.neroland.neroarcade.cabinet;

import net.minecraft.core.BlockPos;

import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.game.ResolvedGame;

/**
 * What a finished match tells the rest of the mod (003 §6.1), after the leaderboard write: threshold events,
 * progression gates, the Exchange play count, the tournament and morale seams. Called from {@code MatchHost.finish}
 * only, at most once per match, never for an {@code ABANDONED} match. A bench test play and a plain unit test get
 * {@link #NONE}. Server thread only.
 */
@FunctionalInterface
public interface MatchListener {

    MatchListener NONE = end -> {
    };

    void onMatchEnd(MatchEnd end);

    /**
     * One finished match.
     *
     * @param result    the bus result (no seats for a hot-seat match: turns are not identities)
     * @param outcome   what the leaderboard write changed ({@link RecordOutcome#NONE} for a hot-seat match)
     * @param hotSeat   true for a {@code turns} match (never credited)
     * @param firstPlay true for the first match since the cartridge went in (the Exchange counts one play per
     *                  insertion)
     */
    record MatchEnd(BusMatchResult result, ResolvedGame game, RecordOutcome outcome, boolean hotSeat,
            boolean firstPlay, BlockPos hostPos) {
    }
}
