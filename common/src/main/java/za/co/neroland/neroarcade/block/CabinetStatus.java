package za.co.neroland.neroarcade.block;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * The cabinet's front status strip ({@code arcade_cabinet} blockstate property {@code status}). Names are the
 * asset contract: lowercase, exactly as the blockstate JSON spells them.
 */
public enum CabinetStatus implements StringRepresentable {
    OFF,
    READY,
    PLAYING,
    LINKED,
    LOCKED,
    NO_POWER;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@code neroarcade.cabinet.status.ready} — the word the screen and the action bar show. */
    public String langKey() {
        return "neroarcade.cabinet.status." + getSerializedName();
    }
}
