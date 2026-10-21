package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;

/**
 * PACE: how often the match simulates. The base clock always runs at 20 Hz; with divisor {@code d} the
 * runtime simulates only when {@code tick % d == 0}, so a larger divisor is a slower game. Pure.
 */
public interface PaceModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.PACE;
    }

    /** 1..{@code Budgets.MAX_PACE_DIVISOR}; the runtime clamps. */
    int divisor(ModuleContext ctx);
}
