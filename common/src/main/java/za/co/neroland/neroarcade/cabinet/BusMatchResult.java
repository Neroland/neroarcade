package za.co.neroland.neroarcade.cabinet;

import java.util.List;
import java.util.UUID;

import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * A finished match as the bus saw it (003 §6.1): produced only inside {@code MatchHost.finish}, server side,
 * once per match. Runtime-opponent seats produce no {@link SeatResult}.
 */
public record BusMatchResult(String lineageRoot, String cartridgeId, List<SeatResult> seats, int durationTicks,
        long seed, MatchResult.Outcome outcome, EndedBy endedBy, long finalHash) {

    public enum EndedBy {
        RULE,
        TIMEOUT,
        ABANDONED,
        ADMIN
    }

    /** One human seat's line. {@code rank} is 1-based; ties share a rank. */
    public record SeatResult(UUID uuid, int seat, int score, int rank, boolean dropped) {
    }

    public BusMatchResult {
        seats = List.copyOf(seats);
    }
}
