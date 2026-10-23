package za.co.neroland.neroarcade.compat.palette;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.palette.Finish;
import za.co.neroland.nerolandcore.palette.PaletteRegistry;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/**
 * Theme colours through Neroland Core's palette contract (003 §10, audit A.12) — the one seam that is real in 0.1.0,
 * and it needs no NeroDecor dependency: Core owns {@link PaletteRegistry}. NeroArcade registers one
 * {@code neroarcade:<theme>} finish per shipped theme (its signature colour); anything that reads Core's palette
 * (a decor mod's dashboard, say) can show arcade colours, and a pack that replaces a finish recolours that theme's
 * cartridge band. {@link #NO_OP} returns the built-in fallback.
 */
public interface PaletteSeam {

    /** The signature colour ({@code 0xRRGGBB}) of a theme by name (e.g. {@code neon_void}), else {@code fallback}. */
    int signature(String theme, int fallback);

    PaletteSeam NO_OP = (theme, fallback) -> fallback;

    /** The four shipped themes' signature colours (004 §5, palette index 10). */
    Map<String, Integer> SIGNATURES = signatures();

    private static Map<String, Integer> signatures() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("neon_void", 0xFF3FA4);
        out.put("hangar", 0x7FD7FF);
        out.put("cindara_dusk", 0xFF7A3C);
        out.put("terminal_green", 0x58E07A);
        return java.util.Collections.unmodifiableMap(out);
    }

    /** Reads Core's palette, falling back when the finish is missing. */
    PaletteSeam CORE = (theme, fallback) -> {
        if (theme == null || !theme.matches("[a-z0-9_]{1,32}")) {
            return fallback;
        }
        return PaletteRegistry.getFinish(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, theme))
                .map(Finish::rgb).orElse(fallback);
    };

    /** Registers the four {@code neroarcade:} finishes with Core (common init, once). */
    static void registerFinishes() {
        SIGNATURES.forEach((theme, rgb) -> PaletteRegistry.register(Finish.accent(
                Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, theme), "Arcade " + theme.replace('_', ' '),
                rgb)));
    }
}
