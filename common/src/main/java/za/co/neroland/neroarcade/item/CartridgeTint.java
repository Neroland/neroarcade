package za.co.neroland.neroarcade.item;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.SlotEntry;

/**
 * The cartridge band tint ({@code custom_model_data} colors[0], 004 "Item ids"): the THEME chip's signature
 * colour (palette index 10), grey for blank or unknown themes. Keyed by the theme module's name, never compared
 * against another mod's id.
 */
public final class CartridgeTint {

    /** Blank cartridges, blank chips and themes this table does not know. */
    public static final int GREY = 0x8A93A6;

    private static final Map<String, Integer> SIGNATURE = Map.of(
            "neon_void", 0xFF3FA4,
            "hangar", 0x7FD7FF,
            "cindara_dusk", 0xFF7A3C,
            "terminal_green", 0x58E07A);

    private CartridgeTint() {
    }

    /** The signature colour of the cartridge's theme (an empty THEME slot is the default theme). */
    public static int of(GameDef def) {
        List<SlotEntry> theme = def.slot(ModuleType.THEME);
        Identifier id = theme.isEmpty() ? ModuleDefaults.THEME : theme.get(0).id();
        return ofTheme(id);
    }

    public static int ofTheme(Identifier themeModule) {
        String path = themeModule.getPath();
        String name = path.startsWith("theme/") ? path.substring("theme/".length()) : path;
        // Core's palette first (a pack may recolour a theme's finish), the built-in signature as the fallback
        return za.co.neroland.neroarcade.compat.Seams.palette().signature(name, SIGNATURE.getOrDefault(name, GREY));
    }
}
