package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.runtime.RuntimeOpponent;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * PAWN: what each seat controls. Pure (see {@link ArcadeModule}). The runtime calls {@link #applyInput} for
 * every seat, then {@link #tick} for every seat (two separate loops), and only for seats that are alive and
 * currently have a pawn on the board.
 */
public interface PawnModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.PAWN;
    }

    /** Creates the seat's pawn entity (and segments) and records it with {@code state.setPawnId}. */
    void spawn(ModuleContext ctx, int seat, int cellX, int cellY);

    /** Turns intent into velocity / direction / fire request. */
    void applyInput(ModuleContext ctx, int seat, Intent intent);

    /** Integrates motion; self-collision; projectile spawn if FIRES and requested. */
    void tick(ModuleContext ctx, int seat);

    /**
     * Collision pass: a lethal HAZARD, an enemy PROJECTILE or (with {@code LETHAL_TO_PAWNS}) another seat's pawn
     * touched this seat's body. The runtime applies the outcome: {@link HitOutcome#DAMAGE} calls
     * {@code state.damageSeat(seat, 1)} (SHIELD still absorbs it), {@link HitOutcome#DEFLECT} reflects the
     * toucher, {@link HitOutcome#IGNORE} does nothing. Default DAMAGE. The hook itself must not call
     * {@code damageSeat} for this hit.
     */
    default HitOutcome onHit(ModuleContext ctx, int seat, int entityId) {
        return HitOutcome.DAMAGE;
    }

    /** After the PICKUP's own {@code onCollect}. */
    default void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
    }

    /** Default: despawn the seat's body, then {@link #spawn} at the arena's spawn cell. */
    default void onRespawn(ModuleContext ctx, int seat) {
        ctx.state().removeSeatBody(seat);
        ModuleContext arenaCtx = ctx.withProps(ctx.game().arenaProps(), 0);
        int[] cell = ctx.game().arena().spawnCell(arenaCtx, seat);
        spawn(ctx, seat, cell[0], cell[1]);
    }

    /**
     * Raw buttons for a runtime-driven seat, asked every {@code ArcadeRuntime.OPPONENT_CADENCE} simulated ticks
     * and held between. Goes through CONTROL and MODIFIER like a human's. Pure: a function of the context only.
     * Default: {@link RuntimeOpponent#greedy}.
     */
    default SeatInput opponentInput(ModuleContext ctx, int seat) {
        return RuntimeOpponent.greedy(ctx, seat);
    }
}
