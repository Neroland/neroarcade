package za.co.neroland.neroarcade.module.pack.modifier;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModifierModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;

/**
 * {@code neroarcade:modifier/fog} — only a circle around each pawn is visible. No flags.
 *
 * <ul>
 *   <li>{@code radius} — the visible radius in cells.</li>
 * </ul>
 *
 * <p>Sim-side this only calls {@code setFogRadius(radius)} at match start: the radius is a render hint the client
 * masks the screen with (spectators too, 001 §7) and the simulation never reads it. Because {@code fogRadius} is part
 * of the keyframe, a fogged cartridge hashes differently from the same game without fog — expected; cells,
 * entities and seats evolve identically. Odd combinations: none. Stateless; pure.
 */
public final class FogModifier implements ModifierModule {

    public static final FogModifier INSTANCE = new FogModifier();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "modifier/fog");

    private static final List<PropertySpec> PROPERTIES = List.of(PropertySpec.integer("radius", 5, 2, 12));

    private FogModifier() {
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
    public void onMatchStart(ModuleContext ctx) {
        ctx.state().setFogRadius(ctx.props().getInt("radius"));
    }

    /** "Fog · sight 5 cells". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("radius")};
    }
}
