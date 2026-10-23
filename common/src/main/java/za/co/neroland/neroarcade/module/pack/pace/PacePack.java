package za.co.neroland.neroarcade.module.pack.pace;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/**
 * The shipped PACE chips other than the default ({@link SteadyPace}, registered by
 * {@code ArcadeModules.registerDefaults()}), in alphabetical id order.
 */
public final class PacePack {

    private PacePack() {
    }

    public static void registerAll() {
        ModuleRegistry.register(PulsePace.INSTANCE);
        ModuleRegistry.register(RampPace.INSTANCE);
    }
}
