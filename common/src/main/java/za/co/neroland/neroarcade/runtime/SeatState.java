package za.co.neroland.neroarcade.runtime;

/**
 * Seat status flags and the packing of their countdowns. A seat's three countdowns live in one int as three
 * 10-bit fields (SPEED bits 0-9, SHIELD 10-19, STUN 20-29), so a status lasts at most {@link #MAX_TICKS}.
 *
 * <p>This is simulation state only and identifies no player; the seat-to-player mapping lives in the cabinet
 * (Stage 4), never here.
 */
public final class SeatState {

    public static final int SPEED = 1;
    public static final int SHIELD = 2;
    public static final int STUN = 4;

    /** Largest countdown a status can hold. */
    public static final int MAX_TICKS = 1023;

    private SeatState() {
    }

    /** Bit offset of a single status flag's countdown in the packed int. */
    static int shift(int flag) {
        return switch (flag) {
            case SPEED -> 0;
            case SHIELD -> 10;
            case STUN -> 20;
            default -> throw new IllegalArgumentException("not a single status flag: " + flag);
        };
    }

    /** The countdown of one flag in a packed value. */
    public static int ticks(int packed, int flag) {
        return (packed >>> shift(flag)) & MAX_TICKS;
    }

    static int withTicks(int packed, int flag, int ticks) {
        int shift = shift(flag);
        return (packed & ~(MAX_TICKS << shift)) | ((ticks & MAX_TICKS) << shift);
    }
}
