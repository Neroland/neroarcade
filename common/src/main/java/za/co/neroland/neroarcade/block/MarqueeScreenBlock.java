package za.co.neroland.neroarcade.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

import za.co.neroland.neroarcade.bus.MarqueeScreenBlockEntity;
import za.co.neroland.neroarcade.registry.ModBlockEntities;

/**
 * The Marquee Screen ({@code neroarcade:marquee_screen}). Shift-use cycles its {@code scale} 1..4 (004 §8.5); a
 * plain use does nothing — the marquee is never an input. On a Link Bus it mirrors the host's game and, while the
 * host is idle, rolls that cartridge's top ten (003 §5). The drawn surface is visual only; the block stays 1x1.
 */
public class MarqueeScreenBlock extends FacingBlock implements EntityBlock {

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

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarqueeScreenBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.MARQUEE_SCREEN.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<MarqueeScreenBlockEntity>) (lvl, pos, st, m) -> m.serverTick();
    }
}
