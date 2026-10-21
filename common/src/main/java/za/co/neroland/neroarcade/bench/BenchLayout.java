package za.co.neroland.neroarcade.bench;

/**
 * Slot coordinates for the "Build your game" screen (004 §8.1), shared by the server menu and the client screen so
 * the screen can move them without touching the server rules. The panel is 440 x 264 with the player inventory
 * inside it (bottom right), so the whole screen fits a 1080p window at GUI scale 4 (480 x 270):
 *
 * <pre>
 *  x 4..232   the RACK: ten rows of 18 px from y 30, chip slots from x 83 (stack positions 18 px apart)
 *  x 238..436 the PREVIEW (64 x 64 at 242, 33) with the title box and the cartridge TRAY to its right,
 *             the PARAMETERS box under it, and the player inventory at the bottom
 * </pre>
 */
public final class BenchLayout {

    public static final int WIDTH = 440;
    public static final int HEIGHT = 264;

    /** First rack row's top edge; one row per slot type, {@link #RACK_ROW} px apart. */
    public static final int ROWS_TOP = 30;
    /** Chip slot item position: x of stack position 0, y of the first row's item. */
    public static final int RACK_X = 83;
    public static final int RACK_Y = ROWS_TOP + 1;
    public static final int RACK_ROW = 18;
    public static final int STACK_STEP = 18;

    public static final int TRAY_X = 313;
    public static final int TRAY_Y = 67;

    public static final int INVENTORY_X = 256;
    public static final int INVENTORY_Y = 182;

    private BenchLayout() {
    }

    public static int chipX(int index) {
        return RACK_X + BenchSlots.stackAt(index) * STACK_STEP;
    }

    public static int chipY(int index) {
        return RACK_Y + BenchSlots.typeAt(index).ordinal() * RACK_ROW;
    }

    /** Top edge of a rack row. */
    public static int rowTop(int typeOrdinal) {
        return ROWS_TOP + typeOrdinal * RACK_ROW;
    }
}
