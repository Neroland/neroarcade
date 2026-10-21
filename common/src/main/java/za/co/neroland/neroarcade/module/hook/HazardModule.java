package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.runtime.Deflect;

/**
 * HAZARD (stacks up to three): what threatens the pawns. Pure (see {@link ArcadeModule}). Every entity a
 * hazard spawns is of kind HAZARD and tagged with {@code ctx.stackIndex()}, so the collision pass routes hits
 * back to the owning chip by stack index.
 */
public interface HazardModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.HAZARD;
    }

    /** Initial placement. */
    void onMatchStart(ModuleContext ctx);

    /** Spawn / move / despawn hazard entities. */
    void tick(ModuleContext ctx);

    /** False = a hazard that blocks (its entities carry {@code F_SOLID}) but does not damage. */
    default boolean lethal(PropertyValues props) {
        return true;
    }

    /** The runtime calls this first, then {@code PawnModule.onHit} when {@link #lethal}. */
    default void onHitPawn(ModuleContext ctx, int seat, int hazardEntityId) {
    }

    /**
     * The pawn of {@code seat} returned {@link HitOutcome#DEFLECT}. Default: {@link Deflect#reflect} — reflect the
     * velocity component along which the entity entered the pawn's cell and move it one step along the
     * reflected velocity.
     */
    default void onDeflected(ModuleContext ctx, int seat, int hazardEntityId, int pawnEntityId) {
        Deflect.reflect(ctx.state(), hazardEntityId);
    }

    /**
     * A seat's PROJECTILE overlapped this hazard entity. Default: hp −1, removed at 0
     * ({@code MatchState.damageEntity}). The runtime removes the projectile afterwards unless it is
     * {@code F_PERSISTENT}. A chip may {@code addScore(ownerSeat, …)} here; an unshootable hazard overrides this
     * to do nothing.
     */
    default void onShot(ModuleContext ctx, int ownerSeat, int hazardEntityId, int projectileEntityId) {
        ctx.state().damageEntity(hazardEntityId, 1);
    }

    /**
     * The entity reached seat {@code goalSeat}'s goal cell; the runtime has already charged that seat one damage.
     * Default: remove the entity (a serving chip re-serves from its own {@link #tick}).
     */
    default void onGoal(ModuleContext ctx, int hazardEntityId, int goalSeat) {
        ctx.state().remove(hazardEntityId);
    }
}
