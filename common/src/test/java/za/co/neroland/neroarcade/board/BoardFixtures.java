package za.co.neroland.neroarcade.board;

import java.util.List;
import java.util.UUID;

import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.runtime.MatchResult;

/** Shared leaderboard-test results. */
final class BoardFixtures {

    static final String LINEAGE = "abcdef0123456789";
    static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");

    private BoardFixtures() {
    }

    static BusMatchResult result(MatchResult.Outcome outcome, BusMatchResult.SeatResult... seats) {
        return new BusMatchResult(LINEAGE, LINEAGE, List.of(seats), 400, 7L, outcome, BusMatchResult.EndedBy.RULE, 1L);
    }

    static BusMatchResult.SeatResult seat(UUID uuid, int seat, int score, boolean dropped) {
        return new BusMatchResult.SeatResult(uuid, seat, score, 1, dropped);
    }
}
