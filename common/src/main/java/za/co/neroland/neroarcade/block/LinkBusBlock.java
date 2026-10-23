package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neroarcade.bus.LinkBusBlockEntity;
import za.co.neroland.neroarcade.registry.ModBlockEntities;

/**
 * The Link Bus hub ({@code neroarcade:link_bus}): joins up to four cabinets (and any Marquee Screens) reached
 * through at most 16 Link Cables into one game (003 §3). Its block entity runs discovery; the bus id is this
 * block's position.
 */
public class LinkBusBlock extends FacingBlock implements EntityBlock {

    public static final MapCodec<LinkBusBlock> CODEC = BlockCodecs.simple(LinkBusBlock::new);

    public LinkBusBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<LinkBusBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkBusBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.LINK_BUS.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<LinkBusBlockEntity>) (lvl, pos, st, hub) -> hub.serverTick();
    }
}
