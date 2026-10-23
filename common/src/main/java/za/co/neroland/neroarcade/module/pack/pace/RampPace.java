package za.co.neroland.neroarcade.module.pack.pace;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PaceModule;
import za.co.neroland.neroarcade.runtime.Budgets;

/**
 * {@code neroarcade:pace/ramp} — starts slow and steps up to full speed. No flags.
 *
 * <ul>
 *   <li>{@code start_divisor} — the slow-down at the start (1 = full speed already, 4 = a quarter).</li>
 *   <li>{@code every_seconds} — the slow-down drops by one after each interval of this many seconds.</li>
 * </ul>
 *
 * <p>{@code divisor = max(1, start_divisor - tick / (every_seconds * 20))}, where tick is the base tick
 * ({@code ctx.tick()}, equal to {@code state.tick()}, counting paced-out ticks too) so the schedule is exact in
 * wall-clock time. Odd combinations: {@code start_divisor 1} is a steady full-speed game. Stateless; pure.
 */
public final class RampPace implements PaceModule {

    public static final RampPace INSTANCE = new RampPace();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "pace/ramp");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("start_divisor", 2, 1, 4),
            PropertySpec.integer("every_seconds", 20, 5, 120));

    private RampPace() {
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
    public int divisor(ModuleContext ctx) {
        int start = ctx.props().getInt("start_divisor");
        int interval = ctx.props().getInt("every_seconds") * Budgets.TICKS_PER_SECOND;
        return Math.max(1, start - ctx.tick() / interval);
    }

    /** "Ramp 2→1 every 20 s". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("start_divisor"), props.getInt("every_seconds")};
    }
}
