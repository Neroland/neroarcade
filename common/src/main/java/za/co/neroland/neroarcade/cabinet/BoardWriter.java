package za.co.neroland.neroarcade.cabinet;

/**
 * The single leaderboard write path (003 §6.1), handed to a {@link MatchHost} at construction so a test can
 * prove nothing else writes. Stage 2 wires {@link #NONE}: there is no leaderboard yet (PRIVACY.md); Stage 4
 * passes {@code LeaderboardStore::record}.
 */
@FunctionalInterface
public interface BoardWriter {

    BoardWriter NONE = result -> {
    };

    /** Called at most once per match, from {@code MatchHost.finish}, never for an abandoned match. */
    void record(BusMatchResult result);
}
