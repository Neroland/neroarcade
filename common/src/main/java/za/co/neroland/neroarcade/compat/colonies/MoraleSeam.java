package za.co.neroland.neroarcade.compat.colonies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Colony morale from arcade play (003 §10). NeroColonies has no public morale write API in 0.1.0 (opening its own
 * quest hook gate would be namespace abuse), so this seam is a no-op that hears every finished match's place.
 */
public interface MoraleSeam {

    /** The NeroColonies mod id. */
    String MOD_ID = "nerocolonies";

    void onMatchPlayed(ServerLevel level, BlockPos cabinet);

    MoraleSeam NO_OP = (level, cabinet) -> {
    };
}
