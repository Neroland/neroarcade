package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neroarcade.exchange.ArcadeExchangeBlockEntity;
import za.co.neroland.neroarcade.exchange.ExchangeActions;
import za.co.neroland.neroarcade.exchange.ExchangeMenu;
import za.co.neroland.neroarcade.menu.MenuOpener;

/**
 * The Arcade Exchange kiosk ({@code neroarcade:arcade_exchange}), 1x2: using either half opens "Find a game to
 * download" — browse, publish, unpublish, download, heart and fetch drafts. The lower half holds the download tray.
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

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isLower(state) ? new ArcadeExchangeBlockEntity(pos, state) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (controller(level, pos, state) instanceof ArcadeExchangeBlockEntity kiosk) {
            InteractionResult result = MenuOpener.openOrConsume(player, kiosk);
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof ExchangeMenu menu) {
                ExchangeActions.opened(serverPlayer, menu);
            }
            return result;
        }
        return InteractionResult.CONSUME;
    }
}
