package za.co.neroland.neroarcade.board;

import za.co.neroland.neroarcade.cabinet.BusMatchResult;

/**
 * Test-only bridge to the package-private {@link LeaderboardTable#record}, so a test in another package can stand a
 * real table behind a {@code MatchHost}'s board writer. The production write path is unchanged (pinned by
 * {@code LeaderboardWriteOnlyFromMatchHostTest}, which scans the main sources only).
 */
public final class BoardTestAccess {

    private BoardTestAccess() {
    }

    /** Records {@code result} at {@code nowEpoch} with the default ten-row cap and no names (nobody opted in). */
    public static RecordOutcome record(LeaderboardTable table, BusMatchResult result, long nowEpoch) {
        return table.record(result, nowEpoch, 10, u -> null);
    }
}
