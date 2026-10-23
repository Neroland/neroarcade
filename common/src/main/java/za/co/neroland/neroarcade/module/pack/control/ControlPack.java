package za.co.neroland.neroarcade.module.pack.control;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/**
 * The CONTROL slot's non-default chips. The default ({@link FourWayControl}) registers in
 * {@code ArcadeModules.registerDefaults()}; these follow in alphabetical id order.
 */
public final class ControlPack {

    private ControlPack() {
    }

    /** Registers {@code one_button}, {@code twin_stick}, {@code two_way}. Call before the registry freezes. */
    public static void registerAll() {
        ModuleRegistry.register(OneButtonControl.INSTANCE);
        ModuleRegistry.register(TwinStickControl.INSTANCE);
        ModuleRegistry.register(TwoWayControl.INSTANCE);
    }
}
