package za.co.neroland.neroarcade.module;

import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * The ten module slots of a NeroArcade game, in their fixed order.
 *
 * <p><b>Invariant:</b> the order is locked. {@link #ordinal()} equals {@link #slot}, and both are the index
 * into every fixed-order array in the runtime and the order a cartridge's {@code slots} map is written in.
 * Never reorder or renumber; a new slot kind is appended (and bumps the cartridge schema).
 *
 * <p>Only {@link #HAZARD} and {@link #PICKUP} stack (up to three chips); every other slot holds one chip or
 * is empty, in which case the runtime resolves the slot to {@link ModuleDefaults#defaultFor(ModuleType)}.
 */
public enum ModuleType {
    ARENA(0, 1, 0x2FE3FF),
    PAWN(1, 1, 0xFF3FA4),
    RULE(2, 1, 0xFFB347),
    HAZARD(3, 3, 0xFF5C5C),
    PICKUP(4, 3, 0x58E07A),
    CONTROL(5, 1, 0xE8ECF4),
    PACE(6, 1, 0xB48CFF),
    MODIFIER(7, 1, 0x8F62FF),
    MULTIPLAYER(8, 1, 0x22B8D8),
    THEME(9, 1, 0xFF8CCF);

    /** Fixed order 0..9; equals {@link #ordinal()}. */
    public final int slot;
    /** 3 for HAZARD and PICKUP, 1 otherwise. */
    public final int maxStack;
    /** {@code 0xRRGGBB}, the slot colour used everywhere (chips, bench, HUD, wiki). Never meaning on its own. */
    public final int colour;

    /** By {@link #key()}, lowercase; an unknown key is a {@code DataResult} error, never a default. */
    public static final Codec<ModuleType> CODEC = Codec.STRING.comapFlatMap(
            key -> byKey(key).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "unknown module slot: " + key)),
            ModuleType::key);

    ModuleType(int slot, int maxStack, int colour) {
        this.slot = slot;
        this.maxStack = maxStack;
        this.colour = colour;
    }

    /** {@code "arena"} — the codec key and the module-id path prefix. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@code "slot.neroarcade.arena"} = "Arena". */
    public String langKey() {
        return "slot.neroarcade." + key();
    }

    /** One sentence: what this slot decides. */
    public String descLangKey() {
        return langKey() + ".desc";
    }

    /** True for the two stack slots. */
    public boolean stacks() {
        return maxStack > 1;
    }

    public static Optional<ModuleType> byKey(String key) {
        for (ModuleType type : values()) {
            if (type.key().equals(key)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
