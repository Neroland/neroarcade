package za.co.neroland.neroarcade.registry;

import java.util.function.Function;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.block.ArcadeCabinetBlock;
import za.co.neroland.neroarcade.block.ArcadeExchangeBlock;
import za.co.neroland.neroarcade.block.CartridgeDuplicatorBlock;
import za.co.neroland.neroarcade.block.GameBenchBlock;
import za.co.neroland.neroarcade.block.LinkBusBlock;
import za.co.neroland.neroarcade.block.LinkCableBlock;
import za.co.neroland.neroarcade.block.MarqueeScreenBlock;

/**
 * NeroArcade's seven blocks (004 "Registry ids"). The cabinet and the bench are live; the duplicator, exchange,
 * marquee, bus and cable place and render now and get their behaviour in Stage 4. Hardware is metal: a pickaxe
 * mines it ({@code minecraft:mineable/pickaxe}), no tool tier needed.
 */
public final class ModBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroArcadeCommon.MOD_ID);

    public static final RegistryEntry<Block> ARCADE_CABINET = register(ArcadeIds.ARCADE_CABINET,
            key -> new ArcadeCabinetBlock(hardware(key).lightLevel(ArcadeCabinetBlock::lightOf)
                    //? if >=26.3 {
                    /*.pushReaction(PushReaction.IMMOVEABLE)
                    *///?} else {
                    .pushReaction(PushReaction.BLOCK)
                    //?}
            ));

    public static final RegistryEntry<Block> GAME_BENCH = register(ArcadeIds.GAME_BENCH,
            key -> new GameBenchBlock(hardware(key)));

    public static final RegistryEntry<Block> CARTRIDGE_DUPLICATOR = register(ArcadeIds.CARTRIDGE_DUPLICATOR,
            key -> new CartridgeDuplicatorBlock(hardware(key)));

    public static final RegistryEntry<Block> ARCADE_EXCHANGE = register(ArcadeIds.ARCADE_EXCHANGE,
            key -> new ArcadeExchangeBlock(hardware(key)
                    //? if >=26.3 {
                    /*.pushReaction(PushReaction.IMMOVEABLE)
                    *///?} else {
                    .pushReaction(PushReaction.BLOCK)
                    //?}
            ));

    public static final RegistryEntry<Block> MARQUEE_SCREEN = register(ArcadeIds.MARQUEE_SCREEN,
            key -> new MarqueeScreenBlock(hardware(key).lightLevel(state -> 7)));

    public static final RegistryEntry<Block> LINK_BUS = register(ArcadeIds.LINK_BUS,
            key -> new LinkBusBlock(hardware(key)));

    public static final RegistryEntry<Block> LINK_CABLE = register(ArcadeIds.LINK_CABLE,
            key -> new LinkCableBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_CYAN)
                    .strength(0.5F).noOcclusion().sound(SoundType.WOOL)));

    private ModBlocks() {
    }

    private static RegistryEntry<Block> register(String name, Function<ResourceKey<Block>, Block> factory) {
        return BLOCKS.register(name, factory);
    }

    /** Arcade hardware: metal, a little tougher than wood, not see-through-culled (models are partial). */
    private static BlockBehaviour.Properties hardware(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(MapColor.COLOR_BLACK)
                .strength(3.0F, 6.0F)
                .noOcclusion()
                .sound(SoundType.METAL);
    }

    /** Classload-forcing no-op (init step 3). */
    public static void init() {
    }
}
