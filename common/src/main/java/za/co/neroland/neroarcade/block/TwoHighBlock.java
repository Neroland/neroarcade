package za.co.neroland.neroarcade.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * A 1x2 block (the arcade cabinet, the exchange kiosk): {@code half = lower|upper} plus {@code facing}. The lower
 * half is the controller and the only half with a block entity; breaking either half removes both exactly once
 * (the vanilla door pattern), and only the lower half drops the item (its loot table matches {@code half=lower}).
 */
public abstract class TwoHighBlock extends BaseEntityBlock {

    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING =
            ArcadeBlockProperties.FACING;
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Half> HALF =
            ArcadeBlockProperties.HALF;

    protected TwoHighBlock(Properties properties) {
        super(properties);
    }

    /** The lower (controller) position of the block the half at {@code pos} belongs to. */
    public static BlockPos lowerPos(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == Half.UPPER ? pos.below() : pos;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        BlockPos above = pos.above();
        if (level.isOutsideBuildHeight(above) || !level.getWorldBorder().isWithinBounds(above)
                || !level.getBlockState(above).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, Half.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlock(pos.above(), upperState(state), Block.UPDATE_ALL);
    }

    /** The upper half for a lower-half state: only {@code facing} and {@code half} matter to its model. */
    protected BlockState upperState(BlockState lower) {
        return defaultBlockState().setValue(FACING, lower.getValue(FACING)).setValue(HALF, Half.UPPER);
    }

    /** A half whose partner is gone (or is not this block's matching half) becomes air. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Half half = state.getValue(HALF);
        boolean partnerSide = half == Half.LOWER ? direction == Direction.UP : direction == Direction.DOWN;
        if (partnerSide && !(neighbourState.getBlock() == this && neighbourState.getValue(HALF) != half
                && neighbourState.getValue(FACING) == state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    /**
     * Creative (or wrong-tool) break of the upper half: remove the lower half without a drop first, so the
     * block never drops twice and never drops from the upper half.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == Half.UPPER
                && (player.isCreative() || !player.hasCorrectToolForDrops(state))) {
            BlockPos lower = pos.below();
            BlockState lowerState = level.getBlockState(lower);
            if (lowerState.getBlock() == this && lowerState.getValue(HALF) == Half.LOWER) {
                level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, lower, Block.getId(lowerState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    /** True for the lower half (the controller). */
    public static boolean isLower(BlockState state) {
        return state.getValue(HALF) == Half.LOWER;
    }

    /** Convenience for subclasses that need the controller's block entity from either half. */
    public static @Nullable BlockEntity controller(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(lowerPos(pos, state));
    }
}
