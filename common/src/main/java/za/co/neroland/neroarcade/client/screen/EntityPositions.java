package za.co.neroland.neroarcade.client.screen;

import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * Where to draw an entity, in fixed point: the authoritative position by default; the client's interpolation and
 * local-pawn prediction override it ({@code client.match}). Implementations must not allocate.
 */
public interface EntityPositions {

    EntityPositions AUTHORITATIVE = new EntityPositions() {
    };

    default int x(MatchState state, int id) {
        return state.get(id, Entities.X);
    }

    default int y(MatchState state, int id) {
        return state.get(id, Entities.Y);
    }
}
