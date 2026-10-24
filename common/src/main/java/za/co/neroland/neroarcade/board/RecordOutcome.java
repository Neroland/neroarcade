package za.co.neroland.neroarcade.board;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * What one {@code LeaderboardStore.record} changed, for the results strip and the threshold channels (003 §6.2, §9).
 * Bits are runtime seats ({@code BusMatchResult.SeatResult.seat}).
 *
 * @param newBestMask    seats that beat their personal best
 * @param enteredTopMask seats that hold a top-board row after this match
 * @param wins           the board's win tally after this match when it was a win, else 0 ({@code match_won})
 * @param records        the board's record tally after this match when it set a new top score, else 0
 *                       ({@code record_set})
 * @param displaced      players whose top-{@value LeaderboardTable#RECORD_WINDOW} row this match pushed down or off
 *                       the board (the NeroLink {@code RECORD_BEATEN} event, 007 §4). Server side only: it is never
 *                       sent to a client, and nothing records who did the pushing.
 */
public record RecordOutcome(int newBestMask, int enteredTopMask, int wins, int records, List<Displaced> displaced) {

    public static final RecordOutcome NONE = new RecordOutcome(0, 0, 0, 0);

    /**
     * One player whose top row lost ground.
     *
     * @param newRank their rank after the match, or null when they fell off the top window
     */
    public record Displaced(UUID player, @Nullable Integer newRank) {
    }

    public RecordOutcome {
        displaced = displaced == null ? List.of() : List.copyOf(displaced);
    }

    public RecordOutcome(int newBestMask, int enteredTopMask, int wins, int records) {
        this(newBestMask, enteredTopMask, wins, records, List.of());
    }

    public RecordOutcome(int newBestMask, int enteredTopMask) {
        this(newBestMask, enteredTopMask, 0, 0);
    }

    public boolean won() {
        return wins > 0;
    }

    public boolean recordSet() {
        return records > 0;
    }
}
