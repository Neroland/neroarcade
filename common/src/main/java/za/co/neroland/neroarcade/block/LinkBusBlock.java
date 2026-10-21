package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * The Link Bus hub ({@code neroarcade:link_bus}). Placed and rendered now; joining cabinets into one game
 * (discovery through Link Cables, up to four cabinets) arrives in Stage 4.
 */
public class LinkBusBlock extends FacingBlock {

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
}
