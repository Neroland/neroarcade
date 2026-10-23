package za.co.neroland.neroarcade.board;

/**
 * What one {@code LeaderboardStore.record} changed, for the results strip and the threshold channels (003 §6.2, §9).
 * Bits are runtime seats ({@code BusMatchResult.SeatResult.seat}).
 *
 * @param newBestMask    seats that beat their personal best
 * @param enteredTopMask seats that hold a top-board row after this match
 * @param wins           the board's win tally after this match when it was a win, else 0 ({@code match_won})
 * @param records        the board's record tally after this match when it set a new top score, else 0
 *                       ({@code record_set})
 */
public record RecordOutcome(int newBestMask, int enteredTopMask, int wins, int records) {

    public static final RecordOutcome NONE = new RecordOutcome(0, 0, 0, 0);

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
