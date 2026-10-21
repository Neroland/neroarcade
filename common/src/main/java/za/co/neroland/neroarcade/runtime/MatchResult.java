package za.co.neroland.neroarcade.runtime;

/**
 * How a match ended. {@code scores} is per seat (index = seat); the cabinet maps seats to players, the
 * runtime never does. {@link Outcome#ABANDONED} is set by the cabinet (every seat dropped), never by a hook.
 */
public record MatchResult(Outcome outcome, int winnersMask, int[] scores, int ticks, long finalHash) {

    public enum Outcome {
        WIN,
        LOSE,
        DRAW,
        ABANDONED
    }

    public MatchResult {
        scores = scores.clone();
    }

    @Override
    public int[] scores() {
        return scores.clone();
    }
}
