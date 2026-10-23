package za.co.neroland.neroarcade.bus;

/** What a block is to Link Bus discovery (003 §3.2). */
public enum BusNode {
    /** Anything else, or an unloaded position: ends the walk. */
    NONE,
    /** A Link Cable: walked through (up to 16 per bus). */
    CABLE,
    /** A Link Bus hub: the origin; another hub is never walked into (buses do not merge). */
    HUB,
    /** An arcade cabinet's lower half: collected (up to 4), never walked through. */
    CABINET,
    /** A Marquee Screen: collected as a viewer, never walked through, no seat. */
    MARQUEE
}
