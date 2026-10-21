package za.co.neroland.neroarcade.registry;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.bench.GameBenchBlockEntity;
import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;

/**
 * Block-entity types through Neroland Core's {@link RegistrationProvider}.
 *
 * <p><b>Fabric 26.1.2:</b> the two-argument {@code BlockEntityType} constructor is private there (public from 26.2;
 * NeoForge and Forge patch it public on every line), which is why {@code fabric/src/main/resources/
 * neroarcade.accesswidener} widens it — the NeroSecurity precedent.
 */
public final class ModBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroArcadeCommon.MOD_ID);

    /** Lower half only. */
    public static final RegistryEntry<BlockEntityType<ArcadeCabinetBlockEntity>> ARCADE_CABINET =
            BLOCK_ENTITIES.register(ArcadeIds.ARCADE_CABINET,
                    key -> new BlockEntityType<>(ArcadeCabinetBlockEntity::new, Set.of(ModBlocks.ARCADE_CABINET.get())));

    public static final RegistryEntry<BlockEntityType<GameBenchBlockEntity>> GAME_BENCH =
            BLOCK_ENTITIES.register(ArcadeIds.GAME_BENCH,
                    key -> new BlockEntityType<>(GameBenchBlockEntity::new, Set.of(ModBlocks.GAME_BENCH.get())));

    private ModBlockEntities() {
    }

    /** Classload-forcing no-op (init step 3). */
    public static void init() {
    }
}
