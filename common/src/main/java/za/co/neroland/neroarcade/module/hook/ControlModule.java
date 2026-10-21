package za.co.neroland.neroarcade.module.hook;

import java.util.List;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * CONTROL: how the raw seat bitfield becomes an {@link Intent}. Pure (see {@link ArcadeModule}); edge
 * detection uses {@code previous}, the input the previous simulated tick saw.
 */
public interface ControlModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.CONTROL;
    }

    Intent interpret(ModuleContext ctx, int seat, SeatInput now, SeatInput previous);

    /** Lang keys of the HUD legend lines, in order. */
    List<String> legend(PropertyValues props);
}
