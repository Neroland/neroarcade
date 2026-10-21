package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * The Arcade Exchange kiosk ({@code neroarcade:arcade_exchange}), 1x2. Placed and rendered now; publishing and
 * downloading games arrives in Stage 4.
 */
public class ArcadeExchangeBlock extends TwoHighBlock {

    public static final MapCodec<ArcadeExchangeBlock> CODEC = BlockCodecs.simple(ArcadeExchangeBlock::new);

    public ArcadeExchangeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.LOWER));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<ArcadeExchangeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }
}
