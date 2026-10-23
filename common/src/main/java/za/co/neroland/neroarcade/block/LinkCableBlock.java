package za.co.neroland.neroarcade.block;

import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neroarcade.bus.BusRegistry;

/**
 * A Link Cable ({@code neroarcade:link_cable}): six connection booleans set from adjacency to another cable, a
 * Link Bus, a cabinet's lower half or a Marquee Screen (003 §3.1), and {@code active} (the hub sets it while its
 * host counts down or plays). Placing, breaking or changing a neighbour makes the bus rediscover at once (the hub
 * also rediscovers every second as a safety net, which covers explosions and pistons).
 */
public class LinkCableBlock extends Block {

    public static final MapCodec<LinkCableBlock> CODEC = BlockCodecs.simple(LinkCableBlock::new);

    private static final Map<Direction, BooleanProperty> BY_DIRECTION = Map.of(
            Direction.NORTH, ArcadeBlockProperties.NORTH, Direction.SOUTH, ArcadeBlockProperties.SOUTH,
            Direction.EAST, ArcadeBlockProperties.EAST, Direction.WEST, ArcadeBlockProperties.WEST,
            Direction.UP, ArcadeBlockProperties.UP, Direction.DOWN, ArcadeBlockProperties.DOWN);

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);

    public LinkCableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(ArcadeBlockProperties.ACTIVE, false);
        for (BooleanProperty side : BY_DIRECTION.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<LinkCableBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ArcadeBlockProperties.NORTH, ArcadeBlockProperties.SOUTH, ArcadeBlockProperties.EAST,
                ArcadeBlockProperties.WEST, ArcadeBlockProperties.UP, ArcadeBlockProperties.DOWN,
                ArcadeBlockProperties.ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = context.getClickedPos().relative(direction);
            state = state.setValue(BY_DIRECTION.get(direction), connects(context.getLevel().getBlockState(neighbour)));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        BlockState next = state.setValue(BY_DIRECTION.get(direction), connects(neighbourState));
        if (next != state && level instanceof ServerLevel serverLevel) {
            touch(serverLevel, pos);
        }
        return next;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            touch(serverLevel, pos);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            touch(serverLevel, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static void touch(ServerLevel level, BlockPos pos) {
        BusRegistry.SERVER.touch(level.dimension().identifier().toString(), pos);
    }

    /** Cables join cables, the bus hub, a cabinet's lower half and a Marquee Screen. */
    static boolean connects(BlockState neighbour) {
        return neighbour.getBlock() instanceof LinkCableBlock
                || neighbour.getBlock() instanceof LinkBusBlock
                || neighbour.getBlock() instanceof MarqueeScreenBlock
                || neighbour.getBlock() instanceof ArcadeCabinetBlock && TwoHighBlock.isLower(neighbour);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> side : BY_DIRECTION.entrySet()) {
            if (state.getValue(side.getValue())) {
                shape = Shapes.or(shape, arm(side.getKey()));
            }
        }
        return shape;
    }

    private static VoxelShape arm(Direction direction) {
        return switch (direction) {
            case NORTH -> Block.box(5, 5, 0, 11, 11, 5);
            case SOUTH -> Block.box(5, 5, 11, 11, 11, 16);
            case WEST -> Block.box(0, 5, 5, 5, 11, 11);
            case EAST -> Block.box(11, 5, 5, 16, 11, 11);
            case UP -> Block.box(5, 11, 5, 11, 16, 11);
            case DOWN -> Block.box(5, 0, 5, 11, 5, 11);
        };
    }
}
