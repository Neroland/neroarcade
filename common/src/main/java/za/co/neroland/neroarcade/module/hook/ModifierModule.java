package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;

/**
 * MODIFIER: a twist on the whole game (wrap, mirror, fog, gravity). Every hook defaults to identity. Pure.
 */
public interface ModifierModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.MODIFIER;
    }

    /** Set constants: {@code setGravityPercent}, {@code setWrap}, {@code setFogRadius}. */
    default void onMatchStart(ModuleContext ctx) {
    }

    /** Mirror flips axes here. */
    default Intent transformIntent(ModuleContext ctx, int seat, Intent in) {
        return in;
    }

    /** Runs after hazards/pickups/collisions and before the rule. */
    default void afterTick(ModuleContext ctx) {
    }
}
