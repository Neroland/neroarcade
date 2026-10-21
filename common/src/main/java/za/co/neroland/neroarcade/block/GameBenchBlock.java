package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neroarcade.bench.GameBenchBlockEntity;
import za.co.neroland.neroarcade.menu.MenuOpener;
import za.co.neroland.neroarcade.registry.ModBlockEntities;

/**
 * The Game Bench ({@code neroarcade:game_bench}): opens "Build your game". {@code cartridge} shows the tray stub;
 * {@code editing} (BER only) is true while someone has the bench open or a test play runs.
 */
public class GameBenchBlock extends BaseEntityBlock {

    public static final MapCodec<GameBenchBlock> CODEC = BlockCodecs.simple(GameBenchBlock::new);

    public GameBenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ArcadeBlockProperties.FACING, Direction.NORTH)
                .setValue(ArcadeBlockProperties.CARTRIDGE, false)
                .setValue(ArcadeBlockProperties.EDITING, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<GameBenchBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ArcadeBlockProperties.FACING, ArcadeBlockProperties.CARTRIDGE, ArcadeBlockProperties.EDITING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ArcadeBlockProperties.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ArcadeBlockProperties.FACING, rotation.rotate(state.getValue(ArcadeBlockProperties.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(ArcadeBlockProperties.FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GameBenchBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.GAME_BENCH.get(), (lvl, pos, st, bench) -> bench.serverTick());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof GameBenchBlockEntity bench) {
            return MenuOpener.openOrConsume(player, bench);
        }
        return InteractionResult.CONSUME;
    }
}
