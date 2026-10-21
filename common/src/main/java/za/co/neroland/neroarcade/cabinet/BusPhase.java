package za.co.neroland.neroarcade.cabinet;

import java.util.Locale;

/**
 * The host's phase (003 §4): {@code IDLE → LOBBY → COUNTDOWN → PLAYING → RESULTS → (LOBBY | IDLE)}. The ordinal
 * is the wire value of {@code MatchPhasePayload}: append only.
 */
public enum BusPhase {
    IDLE,
    LOBBY,
    COUNTDOWN,
    PLAYING,
    RESULTS;

    /** True while a runtime exists and seats may not join. */
    public boolean running() {
        return this == COUNTDOWN || this == PLAYING;
    }

    public String langKey() {
        return "neroarcade.phase." + name().toLowerCase(Locale.ROOT);
    }
}
