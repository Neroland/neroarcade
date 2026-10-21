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
 * {@code neroarcade:theme/neon_void} — the default theme: the family palette on a dark field, in four
 * variants. Ids only; the client resolves them. Stateless.
 */
public final class NeonVoidTheme implements ThemeModule {

    public static final NeonVoidTheme INSTANCE = new NeonVoidTheme();

    private static final List<PropertySpec> PROPERTIES = List.of(PropertySpec.integer("variant", 0, 0, 3));

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
                id("palette/neon_void_" + props.getInt("variant")),
                id("sprites/neon_void"),
                id("arcade.start"),
                id("arcade.pickup"),
                id("arcade.end"));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neroarcade", path);
    }
}
