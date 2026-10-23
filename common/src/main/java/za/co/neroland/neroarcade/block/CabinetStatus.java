package za.co.neroland.neroarcade.block;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.cabinet.BusPhase;

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

    /**
     * The strip from what the cabinet holds. Pure. {@code OFF} only with no cartridge; a readable cartridge with no
     * host (asleep but ready) is {@code READY} — the client runs the attract loop locally, so a loaded cabinet looks
     * alive whether or not the server is simulating it (004 A3, review 005a F9). Sleep governs {@code MatchHost}
     * only.
     *
     * @param phase the host's phase, or null when the cabinet is asleep
     */
    public static CabinetStatus of(boolean hasCartridge, boolean readable, boolean power, @Nullable BusPhase phase) {
        if (!hasCartridge) {
            return CabinetStatus.OFF;
        }
        if (!readable) {
            return CabinetStatus.LOCKED;
        }
        if (!power) {
            return CabinetStatus.NO_POWER;
        }
        if (phase == null) {
            return CabinetStatus.READY;
        }
        return switch (phase) {
            case COUNTDOWN, PLAYING, RESULTS -> CabinetStatus.PLAYING;
            default -> CabinetStatus.READY;
        };
    }
}
