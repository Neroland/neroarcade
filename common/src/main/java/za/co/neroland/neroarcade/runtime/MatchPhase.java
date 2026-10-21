package za.co.neroland.neroarcade.runtime;

/** The phase of a match. The ordinal is the wire/hash value: append only. */
public enum MatchPhase {
    LOBBY,
    COUNTDOWN,
    PLAYING,
    ENDED
}
