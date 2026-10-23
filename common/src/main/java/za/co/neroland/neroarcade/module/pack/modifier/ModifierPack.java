package za.co.neroland.neroarcade.module.pack.modifier;

import za.co.neroland.neroarcade.module.ModuleRegistry;

/** The shipped MODIFIER chips (the slot has no default), in alphabetical id order. */
public final class ModifierPack {

    private ModifierPack() {
    }

    public static void registerAll() {
        ModuleRegistry.register(FogModifier.INSTANCE);
        ModuleRegistry.register(LowGravityModifier.INSTANCE);
        ModuleRegistry.register(MirrorModifier.INSTANCE);
        ModuleRegistry.register(WrapAroundModifier.INSTANCE);
    }
}
