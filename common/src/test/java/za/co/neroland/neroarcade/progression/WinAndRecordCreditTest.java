package za.co.neroland.neroarcade.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.cabinet.BusMatchResult.SeatResult;
import za.co.neroland.neroarcade.runtime.MatchResult;

/** Who a finished match credits: the win stat and the {@code first_record} gate. */
class WinAndRecordCreditTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static BusMatchResult result(MatchResult.Outcome outcome, SeatResult... seats) {
        return new BusMatchResult("root", "cart", List.of(seats), 100, 1L, outcome, BusMatchResult.EndedBy.RULE, 0L);
    }

    @Test
    void soloWinCreditsItsSeat() {
        assertEquals(List.of(A), ArcadeMatchEnds.winners(result(MatchResult.Outcome.WIN, new SeatResult(A, 0, 10, 1, false))));
    }

    @Test
    void lossAndDroppedSeatsCreditNobody() {
        assertTrue(ArcadeMatchEnds.winners(result(MatchResult.Outcome.LOSE, new SeatResult(A, 0, 10, 1, false))).isEmpty());
        assertTrue(ArcadeMatchEnds.winners(result(MatchResult.Outcome.WIN, new SeatResult(A, 0, 10, 1, true))).isEmpty());
    }

    @Test
    void versusWinCreditsOnlyFirstPlace() {
        assertEquals(List.of(B), ArcadeMatchEnds.winners(result(MatchResult.Outcome.WIN,
                new SeatResult(A, 0, 5, 2, false), new SeatResult(B, 1, 9, 1, false))));
    }

    @Test
    void recordHolderIsTheBestEnteredSeat() {
        BusMatchResult r = result(MatchResult.Outcome.WIN, new SeatResult(A, 0, 5, 2, false), new SeatResult(B, 1, 9, 1, false));
        assertEquals(Optional.of(B), ArcadeMatchEnds.recordHolder(r, 0b11));
        assertEquals(Optional.of(A), ArcadeMatchEnds.recordHolder(r, 0b01));
        assertEquals(Optional.empty(), ArcadeMatchEnds.recordHolder(r, 0));
    }

    @Test
    void tiedRecordCreditsNobody() {
        BusMatchResult r = result(MatchResult.Outcome.DRAW, new SeatResult(A, 0, 9, 1, false), new SeatResult(B, 1, 9, 1, false));
        assertEquals(Optional.empty(), ArcadeMatchEnds.recordHolder(r, 0b11));
    }
}
