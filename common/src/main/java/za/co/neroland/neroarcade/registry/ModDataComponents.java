package za.co.neroland.neroarcade.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.item.StoredCartridge;

/**
 * Item data components through Neroland Core's {@link RegistrationProvider} (NeroNotes precedent: persistent
 * codec only; network sync falls back to it). Registered first in init step 3, before the items that use them.
 */
public final class ModDataComponents {

    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroArcadeCommon.MOD_ID);

    /** A module chip's module id ({@code neroarcade:pawn/cursor}). No player data. */
    public static final RegistryEntry<DataComponentType<Identifier>> MODULE_ID =
            COMPONENTS.register("module_id", key -> DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .build());

    /**
     * A cartridge's game, kept as canonical NBT ({@link StoredCartridge}; read through the validator). Carries
     * the author UUID (erasure only) and an opt-in credit string.
     */
    public static final RegistryEntry<DataComponentType<StoredCartridge>> GAME_DEF =
            COMPONENTS.register("game_def", key -> DataComponentType.<StoredCartridge>builder()
                    .persistent(StoredCartridge.CODEC)
                    .build());

    private ModDataComponents() {
    }

    /** Classload-forcing no-op (init step 3, first). */
    public static void init() {
    }
}
