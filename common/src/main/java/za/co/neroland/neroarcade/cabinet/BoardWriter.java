package za.co.neroland.neroarcade.cabinet;

import za.co.neroland.neroarcade.board.RecordOutcome;

/**
 * The single leaderboard write path (003 §6.1), handed to a {@link MatchHost} at construction so a test can
 * prove nothing else writes. A cabinet host gets {@code board.Boards.writer(server)}, which reaches the
 * package-private {@code LeaderboardStore.record}; a bench test play gets {@link #NONE}.
 */
@FunctionalInterface
public interface BoardWriter {

    BoardWriter NONE = result -> RecordOutcome.NONE;

    /**
     * Called at most once per match, from {@code MatchHost.finish}, never for an abandoned or hot-seat match.
     *
     * @return per-seat "new best" flags for the results strip
     */
    RecordOutcome record(BusMatchResult result);
}
