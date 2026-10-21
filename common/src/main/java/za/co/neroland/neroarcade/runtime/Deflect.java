package za.co.neroland.neroarcade.runtime;

/**
 * The default bounce of an entity off a pawn that returned {@code HitOutcome.DEFLECT}. Integer-only and pure;
 * {@code HazardModule.onDeflected}'s default body and the runtime's projectile deflection both call it, so a chip
 * that overrides the hook can still fall back to it.
 */
public final class Deflect {

    private Deflect() {
    }

    /**
     * Reflects the velocity component along which entity {@code id} entered its current cell: compares the
     * cell of {@code (x - vx, y - vy)} with the current one per axis; if both changed both are reflected, if
     * one changed that one is; if neither changed the larger {@code |v|} is reflected (a tie reflects VY).
     * Then the entity moves one step along the reflected velocity (wrapping where the board wraps). A missing
     * id is ignored.
     */
    public static void reflect(MatchState state, int id) {
        if (!state.exists(id)) {
            return;
        }
        int x = state.get(id, Entities.X);
        int y = state.get(id, Entities.Y);
        int vx = state.get(id, Entities.VX);
        int vy = state.get(id, Entities.VY);
        boolean xChanged = ((x - vx) >> MatchState.FP_SHIFT) != (x >> MatchState.FP_SHIFT);
        boolean yChanged = ((y - vy) >> MatchState.FP_SHIFT) != (y >> MatchState.FP_SHIFT);
        if (xChanged || yChanged) {
            if (xChanged) {
                vx = -vx;
            }
            if (yChanged) {
                vy = -vy;
            }
        } else if (Math.abs(vx) > Math.abs(vy)) {
            vx = -vx;
        } else {
            vy = -vy;
        }
        state.set(id, Entities.VX, vx);
        state.set(id, Entities.VY, vy);
        state.set(id, Entities.X, state.wrapX(x + vx));
        state.set(id, Entities.Y, state.wrapY(y + vy));
    }
}
