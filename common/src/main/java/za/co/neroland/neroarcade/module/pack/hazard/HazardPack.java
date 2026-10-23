package za.co.neroland.neroarcade.module.pack.hazard;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/**
 * The HAZARD chips (HAZARD has no empty-slot default): registered in alphabetical id order. Called from
 * {@code ArcadeModules.init()} after the defaults.
 */
public final class HazardPack {

    private HazardPack() {
    }

    public static void registerAll() {
        ModuleRegistry.register(BouncingBallHazard.INSTANCE);
        ModuleRegistry.register(ChaserHazard.INSTANCE);
        ModuleRegistry.register(FallingBlocksHazard.INSTANCE);
        ModuleRegistry.register(LaserSweepHazard.INSTANCE);
        ModuleRegistry.register(ShrinkingFloorHazard.INSTANCE);
    }
}
