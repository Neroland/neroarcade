package za.co.neroland.neroarcade.module.pack.arena;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/**
 * The ARENA slot's non-default chips. {@code grid_16} (the default) registers through
 * {@code ArcadeModules.registerDefaults()}; this registers the rest in alphabetical id order.
 */
public final class ArenaPack {

    private ArenaPack() {
    }

    /** {@code grid_32}, {@code ring_24}, {@code strip_9}. Init only (before the registry freezes). */
    public static void registerAll() {
        ModuleRegistry.register(Grid32Arena.INSTANCE);
        ModuleRegistry.register(Ring24Arena.INSTANCE);
        ModuleRegistry.register(Strip9Arena.INSTANCE);
    }
}
