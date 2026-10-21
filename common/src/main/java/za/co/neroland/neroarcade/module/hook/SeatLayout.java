package za.co.neroland.neroarcade.module.hook;

import java.util.Arrays;

/**
 * Per-seat viewport in cells, decided once at match start by the MULTIPLAYER chip. With a shared board every
 * viewport is the whole arena. Arrays are defensively copied, so the layout is immutable.
 */
public record SeatLayout(int seats, boolean sharedBoard, int[] viewportX, int[] viewportY, int[] viewportW,
        int[] viewportH) {

    public SeatLayout {
        viewportX = viewportX.clone();
        viewportY = viewportY.clone();
        viewportW = viewportW.clone();
        viewportH = viewportH.clone();
    }

    /** Every seat sees the whole {@code width} x {@code height} arena. */
    public static SeatLayout shared(int seats, int width, int height) {
        int[] zero = new int[seats];
        int[] w = new int[seats];
        int[] h = new int[seats];
        Arrays.fill(w, width);
        Arrays.fill(h, height);
        return new SeatLayout(seats, true, zero, zero, w, h);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SeatLayout that && seats == that.seats && sharedBoard == that.sharedBoard
                && Arrays.equals(viewportX, that.viewportX) && Arrays.equals(viewportY, that.viewportY)
                && Arrays.equals(viewportW, that.viewportW) && Arrays.equals(viewportH, that.viewportH);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * seats + (sharedBoard ? 1 : 0)) + Arrays.hashCode(viewportW);
    }

    @Override
    public String toString() {
        return "SeatLayout[seats=" + seats + ", shared=" + sharedBoard + "]";
    }
}
