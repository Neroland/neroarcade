package za.co.neroland.neroarcade.bench;

import za.co.neroland.neroarcade.module.ModuleType;

/**
 * The bench container layout. Rack slots 0..13 hold chips in {@link ModuleType} order with three stack positions
 * for HAZARD and PICKUP; slot 14 is the cartridge tray.
 *
 * <pre>
 *  0 ARENA   1 PAWN   2 RULE   3-5 HAZARD   6-8 PICKUP   9 CONTROL   10 PACE   11 MODIFIER
 * 12 MULTIPLAYER   13 THEME   14 TRAY (a Blank Cartridge to SAVE onto, or a cartridge to LOAD / re-save)
 * </pre>
 */
public final class BenchSlots {

    /** Chip positions in the rack. */
    public static final int CHIP_SLOTS = 14;
    /** The cartridge tray. */
    public static final int TRAY = 14;
    /** Container size. */
    public static final int SIZE = 15;

    private static final int[] FIRST = new int[ModuleType.values().length];
    private static final ModuleType[] TYPE_AT = new ModuleType[CHIP_SLOTS];
    private static final int[] STACK_AT = new int[CHIP_SLOTS];

    static {
        int index = 0;
        for (ModuleType type : ModuleType.values()) {
            FIRST[type.ordinal()] = index;
            for (int s = 0; s < type.maxStack; s++) {
                TYPE_AT[index] = type;
                STACK_AT[index] = s;
                index++;
            }
        }
        if (index != CHIP_SLOTS) {
            throw new IllegalStateException("bench rack must be " + CHIP_SLOTS + " slots, is " + index);
        }
    }

    private BenchSlots() {
    }

    /** Rack index of {@code type} at {@code stackIndex}, or -1 if out of range. */
    public static int index(ModuleType type, int stackIndex) {
        if (stackIndex < 0 || stackIndex >= type.maxStack) {
            return -1;
        }
        return FIRST[type.ordinal()] + stackIndex;
    }

    public static ModuleType typeAt(int index) {
        return TYPE_AT[index];
    }

    public static int stackAt(int index) {
        return STACK_AT[index];
    }

    public static boolean isChipSlot(int index) {
        return index >= 0 && index < CHIP_SLOTS;
    }

    /**
     * One bit per {@link ModuleType} (by ordinal) whose rack row holds at least one chip, from {@code filled}
     * (rack index → occupied). The bench's holo-rack lights the chips of these slots. Pure.
     */
    public static int rackMask(java.util.function.IntPredicate filled) {
        int mask = 0;
        for (int i = 0; i < CHIP_SLOTS; i++) {
            if (filled.test(i)) {
                mask |= 1 << TYPE_AT[i].ordinal();
            }
        }
        return mask;
    }
}
