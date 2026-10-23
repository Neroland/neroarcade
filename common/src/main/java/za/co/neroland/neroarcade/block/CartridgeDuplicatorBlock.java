package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neroarcade.duplicator.CartridgeDuplicatorBlockEntity;
import za.co.neroland.neroarcade.menu.MenuOpener;
import za.co.neroland.neroarcade.registry.ModBlockEntities;

/**
 * The Cartridge Duplicator ({@code neroarcade:cartridge_duplicator}): opens "Copy a game" — an original plus a Blank
 * Cartridge make two identical cartridges. {@code copying} is true while a copy runs; {@code original} and
 * {@code blank} show the two top-slot stubs.
 */
public class CartridgeDuplicatorBlock extends FacingBlock implements EntityBlock {

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

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CartridgeDuplicatorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CARTRIDGE_DUPLICATOR.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<CartridgeDuplicatorBlockEntity>) (lvl, pos, st, dup) ->
                dup.serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CartridgeDuplicatorBlockEntity duplicator) {
            return MenuOpener.openOrConsume(player, duplicator);
        }
        return InteractionResult.CONSUME;
    }
}
