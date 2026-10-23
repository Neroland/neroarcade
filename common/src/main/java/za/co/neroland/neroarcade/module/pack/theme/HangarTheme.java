package za.co.neroland.neroarcade.module.pack.theme;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ThemeModule;
import za.co.neroland.neroarcade.module.hook.ThemeSpec;

/**
 * {@code neroarcade:theme/hangar} — graphite deck, hazard yellow and field blue. Ids only; the client resolves them
 * ({@code ThemePalettes.themeOf} maps by theme name to the {@code hangar} sheets and palette). Stateless, never read
 * by the simulation.
 *
 * <p>No properties (Stage 2 fix F10: no property without a visible effect; {@code variant} returns with the Stage 5
 * alternate palettes), so the palette id always names variant 0.
 *
 * <p>Ids: {@code palette/hangar_0}, {@code sprites/hangar}, sounds {@code theme.hangar.start}, {@code game.pickup},
 * {@code theme.hangar.end} (all {@code neroarcade:}, all present in {@code sounds.json}). Odd combinations: none — a
 * theme changes nothing a match does.
 */
public final class HangarTheme implements ThemeModule {

    public static final HangarTheme INSTANCE = new HangarTheme();

    static final String NAME = "hangar";

    private static final Identifier ID = id("theme/" + NAME);

    private static final List<PropertySpec> PROPERTIES = List.of();

    private HangarTheme() {
    }

    @Override
    public Identifier id() {
        return ID;
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
