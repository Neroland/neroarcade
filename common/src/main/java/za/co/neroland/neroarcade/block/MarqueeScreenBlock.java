package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * The Marquee Screen ({@code neroarcade:marquee_screen}). Shift-use cycles its {@code scale} 1..4 (004 §8.5);
 * mirroring a linked game and rolling the top scores arrives in Stage 4.
 */
public class MarqueeScreenBlock extends FacingBlock {

    public static final MapCodec<MarqueeScreenBlock> CODEC = BlockCodecs.simple(MarqueeScreenBlock::new);

    public MarqueeScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(ArcadeBlockProperties.SCALE, 1));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<MarqueeScreenBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ArcadeBlockProperties.SCALE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            int next = state.getValue(ArcadeBlockProperties.SCALE) % 4 + 1;
            level.setBlock(pos, state.setValue(ArcadeBlockProperties.SCALE, next), Block.UPDATE_CLIENTS);
            player.sendOverlayMessage(Component.translatable("block.neroarcade.marquee_screen.scale", next));
        }
        return InteractionResult.SUCCESS;
    }
}
