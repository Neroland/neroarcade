package za.co.neroland.neroarcade.registry;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.bench.GameBenchBlockEntity;
import za.co.neroland.neroarcade.bus.LinkBusBlockEntity;
import za.co.neroland.neroarcade.bus.MarqueeScreenBlockEntity;
import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;
import za.co.neroland.neroarcade.duplicator.CartridgeDuplicatorBlockEntity;
import za.co.neroland.neroarcade.exchange.ArcadeExchangeBlockEntity;

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

    /** The Link Bus hub: discovery and host election (runtime only, nothing saved). */
    public static final RegistryEntry<BlockEntityType<LinkBusBlockEntity>> LINK_BUS =
            BLOCK_ENTITIES.register(ArcadeIds.LINK_BUS,
                    key -> new BlockEntityType<>(LinkBusBlockEntity::new, Set.of(ModBlocks.LINK_BUS.get())));

    /** The Marquee Screen: a viewer of its bus host's screen. */
    public static final RegistryEntry<BlockEntityType<MarqueeScreenBlockEntity>> MARQUEE_SCREEN =
            BLOCK_ENTITIES.register(ArcadeIds.MARQUEE_SCREEN,
                    key -> new BlockEntityType<>(MarqueeScreenBlockEntity::new, Set.of(ModBlocks.MARQUEE_SCREEN.get())));

    /** The Cartridge Duplicator: two input slots, two output slots, the copy timer. */
    public static final RegistryEntry<BlockEntityType<CartridgeDuplicatorBlockEntity>> CARTRIDGE_DUPLICATOR =
            BLOCK_ENTITIES.register(ArcadeIds.CARTRIDGE_DUPLICATOR, key -> new BlockEntityType<>(
                    CartridgeDuplicatorBlockEntity::new, Set.of(ModBlocks.CARTRIDGE_DUPLICATOR.get())));

    /** The Arcade Exchange kiosk (lower half only): the download tray. */
    public static final RegistryEntry<BlockEntityType<ArcadeExchangeBlockEntity>> ARCADE_EXCHANGE =
            BLOCK_ENTITIES.register(ArcadeIds.ARCADE_EXCHANGE, key -> new BlockEntityType<>(
                    ArcadeExchangeBlockEntity::new, Set.of(ModBlocks.ARCADE_EXCHANGE.get())));

    private ModBlockEntities() {
    }

    /** Classload-forcing no-op (init step 3). */
    public static void init() {
    }
}
