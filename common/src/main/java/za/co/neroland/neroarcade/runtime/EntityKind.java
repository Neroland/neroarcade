package za.co.neroland.neroarcade.runtime;

/** What an entity record is. The ordinal is the wire/hash value: append only, never reorder. */
public enum EntityKind {
    PAWN,
    SEGMENT,
    PROJECTILE,
    HAZARD,
    PICKUP,
    EXIT,
    MARKER;

    private static final EntityKind[] VALUES = values();

    /** By ordinal; an unknown ordinal is {@link #MARKER} (inert). */
    public static EntityKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : MARKER;
    }
}
