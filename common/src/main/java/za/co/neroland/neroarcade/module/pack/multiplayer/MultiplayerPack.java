package za.co.neroland.neroarcade.module.pack.multiplayer;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/**
 * The MULTIPLAYER slot's non-default chips. {@link SoloMultiplayer} is the slot default and registers in
 * {@code ArcadeModules.registerDefaults()}; this registers the rest in alphabetical id order.
 */
public final class MultiplayerPack {

    private MultiplayerPack() {
    }

    /** {@code coop}, {@code turns}, {@code versus}. */
    public static void registerAll() {
        ModuleRegistry.register(CoopMultiplayer.INSTANCE);
        ModuleRegistry.register(TurnsMultiplayer.INSTANCE);
        ModuleRegistry.register(VersusMultiplayer.INSTANCE);
    }
}
