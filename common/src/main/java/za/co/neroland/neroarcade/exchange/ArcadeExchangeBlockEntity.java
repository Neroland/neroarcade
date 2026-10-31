package za.co.neroland.neroarcade.exchange;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.registry.ModBlockEntities;

/**
 * The Arcade Exchange kiosk's lower half: the door to the {@link ExchangeMenu}. It holds <b>no items</b> — each
 * viewer's tray belongs to their own menu (review 003b F6). The listings themselves live in the world's
 * {@link ExchangeStore}, so every kiosk shows the same Exchange.
 *
 * <p>Pre-fix worlds saved a shared tray with the kiosk: such an item is read once, dropped into the world the next
 * time the kiosk is opened or broken, and kept in the save only until then.
 *
 * <p>The listing screen (004 §4.5): every {@value #CHECK_TICKS} ticks the server compares the Exchange's version
 * with the one this kiosk last sent and, when it moved, re-sends the top rows ({@link KioskBoard}: titles and heart
 * counts only) in the block-entity update tag. The rows are never saved.
 */
public class ArcadeExchangeBlockEntity extends BlockEntity implements MenuProvider {

    /** Only an item left over from a pre-fix save; empty otherwise. */
    private final SimpleContainer legacyTray = new SimpleContainer(1);

    /** How often the server checks whether the listing screen needs new rows. */
    public static final int CHECK_TICKS = 40;
    private static final String KEY_BOARD = "kiosk_board";

    /** Server: the Exchange version last sent (-1 = never). */
    private int sentVersion = -1;
    /** Client: the rows the listing screen scrolls. */
    private List<KioskBoard.Line> clientBoard = List.of();

    public ArcadeExchangeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCADE_EXCHANGE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neroarcade.arcade_exchange");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        dropLegacy(worldPosition);
        return new ExchangeMenu(containerId, playerInventory, this);
    }

    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
    //?} else {
    /*// Called from the block's onRemove(..) before 1.21.5 (no such BlockEntity hook there).
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    *///?}
        dropLegacy(pos);
    }

    private void dropLegacy(BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemStack stack = legacyTray.getItem(0);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
            legacyTray.clearContent();
            setChanged();
        }
    }

    /** Server tick (lower half only): re-send the screen rows when the Exchange changed. */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % CHECK_TICKS != 0) {
            return;
        }
        int version = ExchangeStore.get(serverLevel.getServer()).version();
        if (version != sentVersion) {
            sentVersion = version;
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Client side: the listing screen's rows (empty = "NO LISTINGS"). */
    public List<KioskBoard.Line> clientBoard() {
        return clientBoard;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (level instanceof ServerLevel serverLevel) {
            List<KioskBoard.Line> rows = KioskBoard.of(ExchangeStore.get(serverLevel.getServer()).browse());
            KioskBoard.LIST_CODEC.encodeStart(NbtOps.INSTANCE, rows).result().ifPresent(t -> tag.put(KEY_BOARD, t));
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
        if (!legacyTray.isEmpty()) {
            //? if >=1.21.6 {
            ContainerHelper.saveAllItems(output, legacyTray.getItems());
            //?} else {
            /*ContainerHelper.saveAllItems(tag, legacyTray.getItems(), registries);
            *///?}
        }
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
        legacyTray.clearContent();
        //? if >=1.21.6 {
        ContainerHelper.loadAllItems(input, legacyTray.getItems());
        //?} else {
        /*ContainerHelper.loadAllItems(tag, legacyTray.getItems(), registries);
        *///?}
        // the update tag's rows (absent in saved data, so a server load always reads none)
        clientBoard = input.read(KEY_BOARD, KioskBoard.LIST_CODEC).orElse(List.of());
    }
}
