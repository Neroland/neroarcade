package za.co.neroland.neroarcade.module.pack.pickup;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/**
 * The PICKUP chips (PICKUP has no empty-slot default): registered in alphabetical id order. Called from
 * {@code ArcadeModules.init()} after the defaults.
 */
public final class PickupPack {

    private PickupPack() {
    }

    public static void registerAll() {
        ModuleRegistry.register(KeyPickup.INSTANCE);
        ModuleRegistry.register(ScoreOrbPickup.INSTANCE);
        ModuleRegistry.register(ShieldPickup.INSTANCE);
        ModuleRegistry.register(SpeedBoostPickup.INSTANCE);
    }
}
