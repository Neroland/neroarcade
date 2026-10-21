package za.co.neroland.neroarcade.block;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** The two halves of a 1x2 block (cabinet, exchange kiosk). The lower half is the controller. */
public enum Half implements StringRepresentable {
    LOWER,
    UPPER;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
