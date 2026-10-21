package za.co.neroland.neroarcade.runtime;

/**
 * Field offsets of an entity record (stride {@link MatchState#ENTITY_STRIDE}) and the entity flag bits.
 * Positions and velocities are fixed point, 256 units per cell. Ids are 16-bit and never {@code 0xFFFF}.
 */
public final class Entities {

    public static final int ID = 0;
    public static final int KIND = 1;
    public static final int X = 2;
    public static final int Y = 3;
    public static final int VX = 4;
    public static final int VY = 5;
    public static final int OWNER = 6;
    public static final int HP = 7;
    public static final int FLAGS = 8;
    public static final int TTL = 9;

    /** Survives collection (the runtime does not remove a collected pickup carrying this flag). */
    public static final int F_PERSISTENT = 1;
    public static final int F_SOLID = 2;
    public static final int F_LETHAL = 4;
    public static final int F_HIDDEN = 8;

    /** No entity (also the wire value for "no pawn"). */
    public static final int NONE = 0xFFFF;

    private Entities() {
    }
}
