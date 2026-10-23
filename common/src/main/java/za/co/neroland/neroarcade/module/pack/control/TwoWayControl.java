package za.co.neroland.neroarcade.module.pack.control;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ControlModule;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * {@code neroarcade:control/two_way} — movement on one axis plus A and B. Flags {@code ONE_AXIS}. Stateless; pure.
 *
 * <p>Property {@code axis} ({@code horizontal} | {@code vertical}, default horizontal): horizontal reads
 * {@code moveX = RIGHT − LEFT} and forces {@code moveY = 0}; vertical reads {@code moveY = DOWN − UP} and forces
 * {@code moveX = 0}. The other axis' keys are dropped. A/B levels and edges exactly as {@link FourWayControl};
 * aim is always 0.
 *
 * <p>Odd combinations: on a pawn without {@code ONE_AXIS} the bench shows {@code TWO_WAY_ON_FREE_PAWN} and the pawn
 * simply moves on the one axis; a {@code ONE_AXIS} pawn of the other axis still moves, because one-axis pawns read
 * whichever of {@code moveX}/{@code moveY} is non-zero (008 one-axis input rule).
 */
public final class TwoWayControl implements ControlModule {

    public static final TwoWayControl INSTANCE = new TwoWayControl();

    public static final String HORIZONTAL = "horizontal";
    public static final String VERTICAL = "vertical";

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "control/two_way");

    private static final List<PropertySpec> PROPERTIES =
            List.of(PropertySpec.enumeration("axis", HORIZONTAL, HORIZONTAL, VERTICAL));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.ONE_AXIS);

    private TwoWayControl() {
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
        return FLAGS;
    }

    @Override
    public Intent interpret(ModuleContext ctx, int seat, SeatInput now, SeatInput previous) {
        int mx = 0;
        int my = 0;
        if (VERTICAL.equals(ctx.props().getEnum("axis"))) {
            my = (now.has(SeatInput.DOWN) ? 1 : 0) - (now.has(SeatInput.UP) ? 1 : 0);
        } else {
            mx = (now.has(SeatInput.RIGHT) ? 1 : 0) - (now.has(SeatInput.LEFT) ? 1 : 0);
        }
        boolean a = now.has(SeatInput.A);
        boolean b = now.has(SeatInput.B);
        return new Intent(mx, my, 0, 0, a, b, a && !previous.has(SeatInput.A), b && !previous.has(SeatInput.B));
    }

    /** One line: which axis moves (the keycap line already names the buttons). */
    @Override
    public List<String> legend(PropertyValues props) {
        return List.of(VERTICAL.equals(props.getEnum("axis")) ? "neroarcade.legend.move_2way_v"
                : "neroarcade.legend.move_2way_h");
    }

    /** "Left-right · A · B" / "Up-down · A · B". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(langKey() + (VERTICAL.equals(props.getEnum("axis")) ? ".dirs_v"
                : ".dirs_h"))};
    }
}
