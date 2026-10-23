package za.co.neroland.neroarcade.module.pack.theme;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ThemeModule;
import za.co.neroland.neroarcade.module.hook.ThemeSpec;

/**
 * {@code neroarcade:theme/neon_void} — the default theme: dark void, magenta pawns, cyan exits (the house look).
 * Ids only; the client resolves them ({@code ThemePalettes.themeOf} maps by theme name). Stateless, never read by
 * the simulation.
 *
 * <p>No properties: the palette variants arrive with the Stage 5 art, and until then a {@code variant} carried by an
 * older cartridge is an unknown property (a bench warning, dropped on the next save) — no chip ships a property
 * without a visible effect in the preview. The palette id therefore always names variant 0.
 *
 * <p>Ids: {@code palette/neon_void_0}, {@code sprites/neon_void}, sounds {@code theme.neon_void.start},
 * {@code game.pickup}, {@code theme.neon_void.end} (all {@code neroarcade:}, all present in {@code sounds.json}).
 * Odd combinations: none — a theme changes nothing a match does.
 */
public final class NeonVoidTheme implements ThemeModule {

    public static final NeonVoidTheme INSTANCE = new NeonVoidTheme();

    /** The theme name: sheet folder, palette and sound-event infix. */
    static final String NAME = "neon_void";

    private static final List<PropertySpec> PROPERTIES = List.of();

    private NeonVoidTheme() {
    }

    @Override
    public Identifier id() {
        return ModuleDefaults.THEME;
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return Set.of();
    }

    @Override
    public ThemeSpec spec(PropertyValues props) {
        return new ThemeSpec(
                id("palette/" + NAME + "_0"),
                id("sprites/" + NAME),
                id("theme." + NAME + ".start"),
                id("game.pickup"),
                id("theme." + NAME + ".end"));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neroarcade", path);
    }
}
