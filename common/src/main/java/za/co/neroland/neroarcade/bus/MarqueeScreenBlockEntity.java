package za.co.neroland.neroarcade.bus;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.registry.ModBlockEntities;

/**
 * The Marquee Screen (003 §5): a viewer of its bus host's snapshot stream, never an input source and never a seat.
 * Persisted: the bus id cache (its {@code scale} lives in the blockstate). Runtime: the host it mirrors, synced to
 * clients through the update tag (the only runtime field that leaves the server). A marquee with no bus shows the
 * static "NERO ARCADE" card; with a bus and an idle host it rolls that cartridge's top ten.
 */
public class MarqueeScreenBlockEntity extends BlockEntity {

    private static final String KEY_BUS = "bus";
    private static final String KEY_DISPLAY_OF = "display_of";

    private long busId;
    private @Nullable BlockPos displayOf;
    private @Nullable BlockPos clientDisplayOf;
    private boolean firstTick = true;

    public MarqueeScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MARQUEE_SCREEN.get(), pos, state);
    }

    public long busId() {
        return busId;
    }

    public @Nullable BlockPos displayOf() {
        return displayOf;
    }

    /** Client side: the host this marquee mirrors, or null (no bus, or a bus with no host). */
    public @Nullable BlockPos clientDisplayOf() {
        return clientDisplayOf;
    }

    /** The hub's assignment: {@code hostPos} null = a bus with no host. */
    public void assignBus(long newBusId, @Nullable BlockPos hostPos) {
        BlockPos display = hostPos == null ? null : hostPos.immutable();
        if (busId == newBusId && Objects.equals(displayOf, display)) {
            return;
        }
        busId = newBusId;
        displayOf = display;
        sync();
    }

    public void leaveBus(long fromBus) {
        if (busId == fromBus) {
            assignBus(0L, null);
        }
    }

    /** Server tick: asks the hub to pick it up after a load, and goes idle when the hub is gone. */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        String dim = serverLevel.dimension().identifier().toString();
        if (firstTick) {
            firstTick = false;
            BusRegistry.SERVER.markDirty(dim, busId);
            BusRegistry.SERVER.touch(dim, worldPosition);
        }
        if (serverLevel.getGameTime() % 20 == 0 && busId != 0 && BusRegistry.SERVER.get(dim, busId) == null) {
            leaveBus(busId);
        }
    }

    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
    //?} else {
    /*// Called from the block's onRemove(..) before 1.21.5 (no such BlockEntity hook there).
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    *///?}
        if (level instanceof ServerLevel serverLevel) {
            BusRegistry.SERVER.touch(serverLevel.dimension().identifier().toString(), pos);
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        output.putLong(KEY_BUS, busId);
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        busId = input.getLongOr(KEY_BUS, 0L);
        long display = input.getLongOr(KEY_DISPLAY_OF, 0L);
        clientDisplayOf = display == 0L ? null : BlockPos.of(display);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (displayOf != null) {
            tag.putLong(KEY_DISPLAY_OF, displayOf.asLong());
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public String toString() {
        return NeroArcadeCommon.MOD_ID + ":marquee_screen@" + worldPosition.toShortString();
    }
}
