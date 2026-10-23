package za.co.neroland.neroarcade.module.pack.pace;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PaceModule;

/**
 * {@code neroarcade:pace/steady} — the default pace: a constant divisor (1 = the full 20 Hz, 2 = 10 Hz...). No flags.
 *
 * <ul>
 *   <li>{@code divisor} — the slow-down: the match simulates on every {@code divisor}-th base tick.</li>
 * </ul>
 *
 * <p>Odd combinations: none. Stateless; pure.
 */
public final class SteadyPace implements PaceModule {

    public static final SteadyPace INSTANCE = new SteadyPace();

    private static final List<PropertySpec> PROPERTIES = List.of(PropertySpec.integer("divisor", 1, 1, 4));

    private SteadyPace() {
    }

    @Override
    public Identifier id() {
        return ModuleDefaults.PACE;
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
    public int divisor(ModuleContext ctx) {
        return ctx.props().getInt("divisor");
    }

    /** "Steady · slow-down 1". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("divisor")};
    }
}
