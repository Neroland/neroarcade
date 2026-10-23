package za.co.neroland.neroarcade.module.pack.pace;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PaceModule;

/**
 * {@code neroarcade:pace/pulse} — alternates full-speed bursts with slow stretches. No flags.
 *
 * <ul>
 *   <li>{@code fast_ticks} — base ticks of each full-speed burst (divisor 1).</li>
 *   <li>{@code slow_ticks} — base ticks of each slow stretch.</li>
 *   <li>{@code slow_divisor} — the slow-down during a slow stretch (2..4).</li>
 * </ul>
 *
 * <p>{@code divisor = (tick % (fast_ticks + slow_ticks)) < fast_ticks ? 1 : slow_divisor}, tick being the base
 * tick ({@code ctx.tick()} = {@code state.tick()}); the cycle starts with a burst. Odd combinations: none — any
 * chip runs under it, timers simply drain slower during slow stretches. Stateless; pure.
 */
public final class PulsePace implements PaceModule {

    public static final PulsePace INSTANCE = new PulsePace();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "pace/pulse");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("fast_ticks", 60, 20, 200),
            PropertySpec.integer("slow_ticks", 60, 20, 200),
            PropertySpec.integer("slow_divisor", 2, 2, 4));

    private PulsePace() {
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
        int fast = ctx.props().getInt("fast_ticks");
        int cycle = fast + ctx.props().getInt("slow_ticks");
        return Math.floorMod(ctx.tick(), cycle) < fast ? 1 : ctx.props().getInt("slow_divisor");
    }

    /** "Pulse 60/60 · slow 2". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("fast_ticks"), props.getInt("slow_ticks"), props.getInt("slow_divisor")};
    }
}
