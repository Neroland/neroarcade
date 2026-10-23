package za.co.neroland.neroarcade.bus;

import net.minecraft.core.BlockPos;

/** The world as Link Bus discovery sees it: one {@link BusNode} per position. Unloaded positions are NONE. */
@FunctionalInterface
public interface BusWorld {

    BusNode nodeAt(BlockPos pos);
}
