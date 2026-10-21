package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;

/**
 * Everything a hook may see. No world, no server, no clock, no I/O.
 *
 * <p><b>Invariant:</b> {@link #state} is the only mutable thing and every mutation goes through
 * {@link MatchState}'s API; {@link #rng} is the only source of randomness; {@link #game} is read-only and is
 * for capability queries (flags, props), never id comparisons.
 *
 * @param props      this module's own resolved properties
 * @param tick       equals {@code state.tick()}; a convenience
 * @param stackIndex 0 for single slots; 0..2 for HAZARD/PICKUP — lets a stacked chip tag its entities
 */
public record ModuleContext(MatchState state, PropertyValues props, Rng rng, ResolvedGame game, int tick,
        int stackIndex) {

    /** The same context for another module's properties (used by default hook bodies, e.g. respawn). */
    public ModuleContext withProps(PropertyValues other, int otherStackIndex) {
        return new ModuleContext(state, other, rng, game, tick, otherStackIndex);
    }
}
