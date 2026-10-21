package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * The Cartridge Duplicator ({@code neroarcade:cartridge_duplicator}). Placed and rendered now; copying
 * (an original plus a blank → an identical copy) arrives with the link update (Stage 4).
 */
public class CartridgeDuplicatorBlock extends FacingBlock {

    public static final MapCodec<CartridgeDuplicatorBlock> CODEC = BlockCodecs.simple(CartridgeDuplicatorBlock::new);

    public CartridgeDuplicatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(ArcadeBlockProperties.COPYING, false)
                .setValue(ArcadeBlockProperties.ORIGINAL, false)
                .setValue(ArcadeBlockProperties.BLANK, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<CartridgeDuplicatorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ArcadeBlockProperties.COPYING, ArcadeBlockProperties.ORIGINAL, ArcadeBlockProperties.BLANK);
    }
}
