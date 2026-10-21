package za.co.neroland.neroarcade.block;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

import za.co.neroland.neroarcade.runtime.SeatInput;

/** The control-deck pad tilt ({@code arcade_cabinet} property {@code pad}): none, n, s, e or w. */
public enum PadDirection implements StringRepresentable {
    NONE,
    N,
    S,
    E,
    W;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The pad shown for a seat's latched input: vertical wins over horizontal, opposite presses cancel. */
    public static PadDirection of(SeatInput input) {
        boolean up = input.has(SeatInput.UP);
        boolean down = input.has(SeatInput.DOWN);
        boolean left = input.has(SeatInput.LEFT);
        boolean right = input.has(SeatInput.RIGHT);
        if (up != down) {
            return up ? N : S;
        }
        if (left != right) {
            return right ? E : W;
        }
        return NONE;
    }
}
