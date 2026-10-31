package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;
import za.co.neroland.neroarcade.cabinet.CabinetInteraction;
import za.co.neroland.neroarcade.registry.ModBlockEntities;

/**
 * The Arcade Cabinet ({@code neroarcade:arcade_cabinet}), 1x2. The deck, status strip, cartridge stub and bus
 * port live on the lower half's blockstate (004); the upper half carries only {@code facing}/{@code half}. Use
 * from either half: insert a cartridge from the hand, take the seat with an empty hand, eject with sneak.
 */
public class ArcadeCabinetBlock extends TwoHighBlock {

    public static final MapCodec<ArcadeCabinetBlock> CODEC = BlockCodecs.simple(ArcadeCabinetBlock::new);

    public ArcadeCabinetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, Half.LOWER)
                .setValue(ArcadeBlockProperties.STATUS, CabinetStatus.OFF)
                .setValue(ArcadeBlockProperties.PAD, PadDirection.NONE)
                .setValue(ArcadeBlockProperties.BTN_A, false)
                .setValue(ArcadeBlockProperties.BTN_B, false)
                .setValue(ArcadeBlockProperties.CARTRIDGE, false)
                .setValue(ArcadeBlockProperties.LINKED, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<ArcadeCabinetBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, ArcadeBlockProperties.STATUS, ArcadeBlockProperties.PAD, ArcadeBlockProperties.BTN_A,
                ArcadeBlockProperties.BTN_B, ArcadeBlockProperties.CARTRIDGE, ArcadeBlockProperties.LINKED);
    }

    /** Block light: 7 while the strip is lit (any status but OFF and NO POWER, so a loaded cabinet glows), else 0. */
    public static int lightOf(BlockState state) {
        return state.getValue(HALF) == Half.LOWER ? state.getValue(ArcadeBlockProperties.STATUS).blockLight() : 0;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isLower(state) ? new ArcadeCabinetBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || !isLower(state)) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.ARCADE_CABINET.get(),
                (lvl, pos, st, cabinet) -> cabinet.serverTick());
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return CabinetInteraction.isCartridgeLike(stack) ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (controller(level, pos, state) instanceof ArcadeCabinetBlockEntity cabinet
                && CabinetInteraction.isCartridgeLike(stack)) {
            return CabinetInteraction.insert(cabinet, serverPlayer, stack);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    //?} else {
    /*// Before 1.21.2 an item use on a block answers with an ItemInteractionResult.
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return CabinetInteraction.isCartridgeLike(stack) ? net.minecraft.world.ItemInteractionResult.SUCCESS
                    : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (controller(level, pos, state) instanceof ArcadeCabinetBlockEntity cabinet
                && CabinetInteraction.isCartridgeLike(stack)) {
            InteractionResult result = CabinetInteraction.insert(cabinet, serverPlayer, stack);
            if (result == InteractionResult.FAIL) {
                return net.minecraft.world.ItemInteractionResult.FAIL;
            }
            return result.consumesAction() ? net.minecraft.world.ItemInteractionResult.CONSUME
                    : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    *///?}

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (controller(level, pos, state) instanceof ArcadeCabinetBlockEntity cabinet) {
            return serverPlayer.isShiftKeyDown()
                    ? CabinetInteraction.eject(cabinet, serverPlayer)
                    : CabinetInteraction.takeSeat(cabinet, serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    // Before 1.21.5 a block entity gets no preRemoveSideEffects(..) call on real removal; run it from here.
    //? if <1.21.5 {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ArcadeCabinetBlockEntity be) {
            be.preRemoveSideEffects(pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    *///?}
}
