package za.co.neroland.neroarcade.module.hook;

/**
 * What a pawn makes of a touch in the collision pass ({@link PawnModule#onHit}). The pawn decides; the runtime
 * applies. The ordinal is not serialised.
 */
public enum HitOutcome {
    /** The runtime calls {@code damageSeat(seat, 1)} (SHIELD still absorbs it); an enemy projectile is removed. */
    DAMAGE,
    /** The toucher bounces off: a hazard through {@link HazardModule#onDeflected}, a projectile is reflected and
     *  now belongs to the deflecting seat. A pawn–pawn DEFLECT does nothing. */
    DEFLECT,
    /** Nothing happens; a projectile flies on. */
    IGNORE
}
