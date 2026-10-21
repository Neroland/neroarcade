package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;

/**
 * MULTIPLAYER: how many seats, how they share the board and lives, and how a rule outcome maps onto seats.
 * Pure (see {@link ArcadeModule}).
 */
public interface MultiplayerModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.MULTIPLAYER;
    }

    /** 1..{@code Budgets.MAX_SEATS}. */
    int seats(PropertyValues props);

    /** Called once at match start; stored in the match state. */
    SeatLayout layout(ModuleContext ctx);

    /** Coop: one life pool; otherwise per seat. */
    boolean sharesLives(PropertyValues props);

    /** Seats outside the mask get {@code Intent.NONE} this tick (turns). Default: every alive seat. */
    default int activeSeatMask(ModuleContext ctx) {
        return ctx.state().aliveSeatMask();
    }

    /** Turn rotation, split-board bookkeeping. */
    default void afterTick(ModuleContext ctx) {
    }

    /** May convert the rule's outcome; must leave the winners mask consistent. */
    RuleOutcome resolve(ModuleContext ctx, RuleOutcome fromRule);

    /** True = this chip supplies a runtime-driven seat when only one human seat exists. */
    default boolean runtimeOpponent(PropertyValues props) {
        return false;
    }
}
