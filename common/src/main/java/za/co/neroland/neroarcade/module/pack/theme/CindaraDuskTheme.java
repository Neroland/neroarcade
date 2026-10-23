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
 * {@code neroarcade:theme/cindara_dusk} — ember reds and dusk amber under a warm sand glow. Ids only; the client
 * resolves them ({@code ThemePalettes.themeOf} maps by theme name to the {@code cindara_dusk} sheets and palette).
 * Stateless, never read by the simulation.
 *
 * <p>No properties (Stage 2 fix F10: no property without a visible effect; {@code variant} returns with the Stage 5
 * alternate palettes), so the palette id always names variant 0.
 *
 * <p>Ids: {@code palette/cindara_dusk_0}, {@code sprites/cindara_dusk}, sounds {@code theme.cindara_dusk.start},
 * {@code game.pickup}, {@code theme.cindara_dusk.end} (all {@code neroarcade:}, all present in {@code sounds.json}).
 * Odd combinations: none — a theme changes nothing a match does.
 */
public final class CindaraDuskTheme implements ThemeModule {

    public static final CindaraDuskTheme INSTANCE = new CindaraDuskTheme();

    static final String NAME = "cindara_dusk";

    private static final Identifier ID = id("theme/" + NAME);

    private static final List<PropertySpec> PROPERTIES = List.of();

    private CindaraDuskTheme() {
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

    /** The Arcade Exchange genre word ({@code neroarcade.tag.dusk}). */
    @Override
    public String genreTag() {
        return "dusk";
    }
}
