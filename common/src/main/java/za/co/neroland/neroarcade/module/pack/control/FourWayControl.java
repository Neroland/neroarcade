package za.co.neroland.neroarcade.module.pack.control;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ControlModule;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * {@code neroarcade:control/four_way} — the default control: up/down/left/right plus A and B. Opposite
 * directions cancel. Without {@code diagonals}, when both axes are held the axis pressed most recently wins
 * (horizontal on a tie), so a turn never needs a release. Stateless; pure.
 *
 * <p>Property {@code diagonals} (default false): true passes both axes through. Aim is always 0. Odd combinations:
 * a {@code ONE_AXIS} pawn reads whichever of {@code moveX}/{@code moveY} is non-zero (008 one-axis input rule), so a
 * four-way pad drives every pawn; no bench warning applies to this chip.
 */
public final class FourWayControl implements ControlModule {

    public static final FourWayControl INSTANCE = new FourWayControl();

    private static final List<PropertySpec> PROPERTIES = List.of(PropertySpec.bool("diagonals", false));

    private FourWayControl() {
    }

    @Override
    public Identifier id() {
        return ModuleDefaults.CONTROL;
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
    public Intent interpret(ModuleContext ctx, int seat, SeatInput now, SeatInput previous) {
        int mx = (now.has(SeatInput.RIGHT) ? 1 : 0) - (now.has(SeatInput.LEFT) ? 1 : 0);
        int my = (now.has(SeatInput.DOWN) ? 1 : 0) - (now.has(SeatInput.UP) ? 1 : 0);
        if (mx != 0 && my != 0 && !ctx.props().getBool("diagonals")) {
            boolean newX = (now.buttons() & ~previous.buttons() & (SeatInput.LEFT | SeatInput.RIGHT)) != 0;
            boolean newY = (now.buttons() & ~previous.buttons() & (SeatInput.UP | SeatInput.DOWN)) != 0;
            if (newY && !newX) {
                mx = 0;
            } else {
                my = 0;
            }
        }
        boolean a = now.has(SeatInput.A);
        boolean b = now.has(SeatInput.B);
        return new Intent(mx, my, 0, 0, a, b, a && !previous.has(SeatInput.A), b && !previous.has(SeatInput.B));
    }

    @Override
    public List<String> legend(PropertyValues props) {
        // the keycap line already names SPACE action · SHIFT 2nd action; only the movement needs saying
        return List.of(props.getBool("diagonals") ? "neroarcade.legend.move_8way" : "neroarcade.legend.move_4way");
    }

    /** "4 directions · A · B" (or 8 with diagonals). */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(langKey() + (props.getBool("diagonals") ? ".dirs8" : ".dirs4"))};
    }
}
