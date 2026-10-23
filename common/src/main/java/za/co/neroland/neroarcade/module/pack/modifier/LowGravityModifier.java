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
 * {@code neroarcade:modifier/low_gravity} — falling things fall slower. No flags.
 *
 * <ul>
 *   <li>{@code percent} — gravity as a percentage of the default (32 units/tick²): 50 gives 16.</li>
 * </ul>
 *
 * <p>At match start {@code setGravityPercent(percent)}. Chips that read {@code state.gravity()} follow it: a runner's
 * hop lasts longer ({@code T = 128·jump / gravity}), falling blocks accelerate slower. Odd combinations: on a board
 * with nothing that reads gravity it changes nothing (runs, no warning). Stateless; pure.
 */
public final class LowGravityModifier implements ModifierModule {

    public static final LowGravityModifier INSTANCE = new LowGravityModifier();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "modifier/low_gravity");

    private static final List<PropertySpec> PROPERTIES = List.of(PropertySpec.integer("percent", 50, 10, 90));

    private LowGravityModifier() {
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
        ctx.state().setGravityPercent(ctx.props().getInt("percent"));
    }

    /** "Low gravity ×0.50" (percent is 10..90, always two digits; a literal percent sign would read as a placeholder). */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("percent")};
    }
}
