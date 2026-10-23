package za.co.neroland.neroarcade.bus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.neroarcade.block.ArcadeCabinetBlock;
import za.co.neroland.neroarcade.block.LinkBusBlock;
import za.co.neroland.neroarcade.block.LinkCableBlock;
import za.co.neroland.neroarcade.block.MarqueeScreenBlock;
import za.co.neroland.neroarcade.block.TwoHighBlock;

/** The live {@link BusWorld}: loaded blocks only, so discovery never loads a chunk (003 §3.3). */
public final class LevelBusWorld implements BusWorld {

    private final Level level;

    public LevelBusWorld(Level level) {
        this.level = level;
    }

    @Override
    public BusNode nodeAt(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return BusNode.NONE;
        }
        return of(level.getBlockState(pos));
    }

    /** What a block state is to discovery. */
    public static BusNode of(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LinkCableBlock) {
            return BusNode.CABLE;
        }
        if (block instanceof LinkBusBlock) {
            return BusNode.HUB;
        }
        if (block instanceof ArcadeCabinetBlock) {
            return TwoHighBlock.isLower(state) ? BusNode.CABINET : BusNode.NONE;
        }
        if (block instanceof MarqueeScreenBlock) {
            return BusNode.MARQUEE;
        }
        return BusNode.NONE;
    }
}
