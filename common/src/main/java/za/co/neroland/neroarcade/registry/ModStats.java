package za.co.neroland.neroarcade.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/**
 * NeroArcade's custom statistics (vanilla {@code minecraft:custom} stat type). They live in the player's vanilla
 * statistics file like any other stat; NeroArcade keeps no copy.
 */
public final class ModStats {

    public static final RegistrationProvider<Identifier> STATS =
            RegistrationProvider.get(Registries.CUSTOM_STAT, NeroArcadeCommon.MOD_ID);

    /** Credited arcade matches the player won (never hot-seat). */
    public static final RegistryEntry<Identifier> MATCHES_WON = STATS.register("matches_won",
            key -> Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "matches_won"));

    private ModStats() {
    }

    public static void init() {
    }
}
