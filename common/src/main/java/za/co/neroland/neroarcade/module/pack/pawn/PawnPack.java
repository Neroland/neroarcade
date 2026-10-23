package za.co.neroland.neroarcade.module.pack.pawn;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/**
 * The PAWN slot's non-default chips. {@code cursor} (the default) registers through
 * {@code ArcadeModules.registerDefaults()}; this registers the rest in alphabetical id order.
 */
public final class PawnPack {

    private PawnPack() {
    }

    /** {@code paddle}, {@code runner}, {@code serpent}, {@code ship}, {@code stacker}. Init only. */
    public static void registerAll() {
        ModuleRegistry.register(PaddlePawn.INSTANCE);
        ModuleRegistry.register(RunnerPawn.INSTANCE);
        ModuleRegistry.register(SerpentPawn.INSTANCE);
        ModuleRegistry.register(ShipPawn.INSTANCE);
        ModuleRegistry.register(StackerPawn.INSTANCE);
    }
}
