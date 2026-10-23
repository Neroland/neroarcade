package za.co.neroland.neroarcade.runtime;

/** Cell values in {@code MatchState.cells}. The numbers are the wire/hash values: append only. */
public final class Cell {

    public static final byte EMPTY = 0;
    public static final byte WALL = 1;
    public static final byte VOID = 2;
    public static final byte EXIT = 3;
    public static final byte EXIT_LOCKED = 4;
    public static final byte LANE = 5;
    /** The goal line of seat 0..3: a HAZARD or another seat's PROJECTILE on it costs that seat a life. */
    public static final byte GOAL_0 = 6;
    public static final byte GOAL_1 = 7;
    public static final byte GOAL_2 = 8;
    public static final byte GOAL_3 = 9;
    /**
     * A settled stack cell (the {@code stacker} pawn writes and clears it; arenas never write it). Solid and
     * non-lethal: it blocks pawns, hazards and the runtime opponent exactly like {@link #WALL}, but row clears and
     * the renderer can tell it apart from the arena's own walls.
     */
    public static final byte BLOCK = 10;

    private Cell() {
    }

    /** True for cells a pawn cannot enter: WALL, EXIT_LOCKED and BLOCK. Goal cells do not block. */
    public static boolean blocks(byte value) {
        return value == WALL || value == EXIT_LOCKED || value == BLOCK;
    }

    /** The goal cell of {@code seat} (0..3). */
    public static byte goal(int seat) {
        return (byte) (GOAL_0 + (seat & 3));
    }

    /** The seat whose goal line this cell is, or -1. */
    public static int goalSeat(byte value) {
        return value >= GOAL_0 && value <= GOAL_3 ? value - GOAL_0 : -1;
    }
}
