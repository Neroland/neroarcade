package za.co.neroland.neroarcade.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.item.ArcadeBlockItem;
import za.co.neroland.neroarcade.item.CartridgeItem;
import za.co.neroland.neroarcade.item.ModuleChipItem;
import za.co.neroland.neroarcade.item.TooltipItem;

/**
 * NeroArcade's items: the one {@code module_chip} (the module is a component), {@code cartridge},
 * {@code blank_cartridge}, {@code blank_chip}, and a block item per block. Block items reference their block
 * lazily, so this class may load before {@link ModBlocks} on every loader.
 */
public final class ModItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroArcadeCommon.MOD_ID);

    public static final RegistryEntry<Item> MODULE_CHIP = ITEMS.register(ArcadeIds.MODULE_CHIP,
            key -> new ModuleChipItem(new Item.Properties().setId(key).stacksTo(16)));

    public static final RegistryEntry<Item> CARTRIDGE = ITEMS.register(ArcadeIds.CARTRIDGE,
            key -> new CartridgeItem(new Item.Properties().setId(key).stacksTo(1)));

    public static final RegistryEntry<Item> BLANK_CARTRIDGE = ITEMS.register(ArcadeIds.BLANK_CARTRIDGE,
            key -> new TooltipItem(new Item.Properties().setId(key).stacksTo(16),
                    "item.neroarcade.blank_cartridge.tooltip"));

    public static final RegistryEntry<Item> BLANK_CHIP = ITEMS.register(ArcadeIds.BLANK_CHIP,
            key -> new TooltipItem(new Item.Properties().setId(key), "item.neroarcade.blank_chip.tooltip"));

    public static final RegistryEntry<BlockItem> ARCADE_CABINET = block(ArcadeIds.ARCADE_CABINET, ModBlocks.ARCADE_CABINET);
    public static final RegistryEntry<BlockItem> GAME_BENCH = block(ArcadeIds.GAME_BENCH, ModBlocks.GAME_BENCH);
    public static final RegistryEntry<BlockItem> CARTRIDGE_DUPLICATOR =
            block(ArcadeIds.CARTRIDGE_DUPLICATOR, ModBlocks.CARTRIDGE_DUPLICATOR);
    public static final RegistryEntry<BlockItem> ARCADE_EXCHANGE = block(ArcadeIds.ARCADE_EXCHANGE, ModBlocks.ARCADE_EXCHANGE);
    public static final RegistryEntry<BlockItem> MARQUEE_SCREEN = block(ArcadeIds.MARQUEE_SCREEN, ModBlocks.MARQUEE_SCREEN);
    public static final RegistryEntry<BlockItem> LINK_BUS = block(ArcadeIds.LINK_BUS, ModBlocks.LINK_BUS);
    public static final RegistryEntry<BlockItem> LINK_CABLE = block(ArcadeIds.LINK_CABLE, ModBlocks.LINK_CABLE);

    private ModItems() {
    }

    private static RegistryEntry<BlockItem> block(String name, Supplier<Block> block) {
        return ITEMS.register(name, key -> new ArcadeBlockItem(block.get(),
                new Item.Properties().setId(key).useBlockDescriptionPrefix(), "block.neroarcade." + name + ".tooltip"));
    }

    /** Classload-forcing no-op (init step 3). */
    public static void init() {
    }
}
