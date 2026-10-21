package za.co.neroland.neroarcade.module.hook;

/**
 * What the RULE (and then MULTIPLAYER) decides at the end of a simulated tick. WIN/LOSE are from the
 * perspective of the seat set named by {@code MatchState.setWinners(mask)}, which must be set before a
 * terminal outcome is returned.
 */
public enum RuleOutcome {
    CONTINUE,
    WIN,
    LOSE,
    DRAW
}
