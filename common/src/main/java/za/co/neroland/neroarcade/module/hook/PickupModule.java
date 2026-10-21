package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;

/**
 * PICKUP (stacks up to three): what the pawns collect. Pure (see {@link ArcadeModule}). Entities are of kind
 * PICKUP, tagged with {@code ctx.stackIndex()}.
 */
public interface PickupModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.PICKUP;
    }

    void onMatchStart(ModuleContext ctx);

    /** Keep pickups on the board, respawn timers, etc. */
    void tick(ModuleContext ctx);

    /**
     * Score, status flags, keys. The runtime removes the entity afterwards unless the hook set
     * {@code Entities.F_PERSISTENT} on it.
     */
    void onCollect(ModuleContext ctx, int seat, int pickupEntityId);
}
