package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.runtime.Budgets;

/**
 * RULE: how a match is won or lost. Pure (see {@link ArcadeModule}). {@link #evaluate} runs once per
 * simulated tick after everything else and must set the winners mask before returning WIN/LOSE/DRAW.
 */
public interface RuleModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.RULE;
    }

    /** Places exits/keys/targets, sets lives ({@code state.setLives}) and the timer. */
    void onMatchStart(ModuleContext ctx);

    RuleOutcome evaluate(ModuleContext ctx);

    /** A seat left the match (or, from tick 0, never existed in a one-seat versus game). Default: nothing. */
    default void onSeatDropped(ModuleContext ctx, int seat) {
    }

    /** Hard stop: the runtime ends the match as DRAW after this many seconds of play, whatever the rule says. */
    default int maxSeconds(PropertyValues props) {
        return Budgets.maxMatchSeconds();
    }
}
