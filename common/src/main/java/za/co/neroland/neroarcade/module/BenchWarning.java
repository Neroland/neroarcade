package za.co.neroland.neroarcade.module;

import java.util.Locale;

/**
 * An informational note the bench shows in grey about a combination of chips that runs but may surprise the
 * builder. <b>Never a refusal</b>: the only refusals are validator errors. Each warning has the lang key
 * {@code neroarcade.bench.warning.<name>}.
 */
public enum BenchWarning {
    SCORE_RULE_WITHOUT_SCORE_SOURCE,
    KEY_WITHOUT_EXIT,
    EXIT_ON_ONE_AXIS,
    AIM_WITHOUT_FIRING_PAWN,
    ONE_BUTTON_FREE_PAWN,
    TWO_WAY_ON_FREE_PAWN,
    CLEAR_BOARD_WITHOUT_PICKUPS,
    VERSUS_NEEDS_SEATS,
    NO_THREAT,
    CROWDED,
    UNKNOWN_PROPERTY;

    public String langKey() {
        return "neroarcade.bench.warning." + name().toLowerCase(Locale.ROOT);
    }
}
