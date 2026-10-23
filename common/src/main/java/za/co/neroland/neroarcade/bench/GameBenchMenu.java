package za.co.neroland.neroarcade.bench;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.item.ModuleChips;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.registry.ModMenus;

/**
 * The Game Bench menu: a vanilla {@link AbstractContainerMenu} over the bench's 15-slot container — 14 rack
 * slots ({@link BenchSlots}) and the cartridge tray — then the player inventory. Chips are templates, never
 * consumed; each rack slot takes one chip of its own slot type. Properties, the title and the actions travel as
 * {@code BenchEditPayload}; the draft comes back as {@code BenchStatePayload}.
 *
 * <p><b>Menu slot indices:</b> 0..13 rack (= {@link BenchSlots} indices), 14 tray, 15..41 player inventory,
 * 42..50 hotbar. <b>Data slots</b> ({@link #addDataSlots}): {@link #DATA_TESTING} (1 while a test play runs),
 * {@link #DATA_GHOST_MASK} (bit i = rack slot i holds a LOAD-materialised chip that cannot be taken, only
 * cleared with {@code CLEAR_SLOT}).
 */
public class GameBenchMenu extends AbstractContainerMenu {

    public static final int DATA_TESTING = 0;
    public static final int DATA_GHOST_MASK = 1;
    public static final int DATA_COUNT = 2;

    public static final int PLAYER_INVENTORY_START = BenchSlots.SIZE;
    public static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    private final Container container;
    private final ContainerData data;
    private final @Nullable GameBenchBlockEntity bench;

    /** Client-side reconstruction (the {@code MenuType} factory). */
    public GameBenchMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(BenchSlots.SIZE), new SimpleContainerData(DATA_COUNT),
                null);
    }

    /** Server side, bound to a bench. */
    public GameBenchMenu(int containerId, Inventory playerInventory, GameBenchBlockEntity bench) {
        this(containerId, playerInventory, bench.inventory(), bench.dataAccess(), bench);
    }

    private GameBenchMenu(int containerId, Inventory playerInventory, Container container, ContainerData data,
            @Nullable GameBenchBlockEntity bench) {
        super(ModMenus.GAME_BENCH.get(), containerId);
        checkContainerSize(container, BenchSlots.SIZE);
        this.container = container;
        this.data = data;
        this.bench = bench;
        for (int i = 0; i < BenchSlots.CHIP_SLOTS; i++) {
            addSlot(new ChipSlot(container, i));
        }
        addSlot(new TraySlot(container));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, BenchLayout.INVENTORY_X + col * 18,
                        BenchLayout.INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, BenchLayout.INVENTORY_X + col * 18, BenchLayout.INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    /** The 15-slot bench container (on the client, the synced copy the screen reads). */
    public Container container() {
        return container;
    }

    /** The bench (server side only). */
    public @Nullable GameBenchBlockEntity bench() {
        return bench;
    }

    /** The bench position (server side only). */
    public @Nullable BlockPos benchPos() {
        return bench == null ? null : bench.getBlockPos();
    }

    public boolean testing() {
        return data.get(DATA_TESTING) != 0;
    }

    public int ghostMask() {
        return data.get(DATA_GHOST_MASK) & 0x3FFF;
    }

    public boolean isGhost(int rackIndex) {
        return BenchSlots.isChipSlot(rackIndex) && (ghostMask() & (1 << rackIndex)) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return bench == null || Container.stillValidBlockEntity(bench, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (bench != null) {
            bench.onMenuClosed(player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < PLAYER_INVENTORY_START) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveIntoBench(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    /** Shift-click from the inventory: a chip to the first free rack slot of its type, a cartridge to the tray. */
    private boolean moveIntoBench(ItemStack stack) {
        for (int i = 0; i < BenchSlots.SIZE; i++) {
            Slot target = slots.get(i);
            if (!target.hasItem() && target.mayPlace(stack)) {
                return moveItemStackTo(stack, i, i + 1, false);
            }
        }
        return false;
    }

    /** A rack slot: one chip whose module fills this slot's type; a LOAD-materialised chip cannot be taken. */
    private final class ChipSlot extends Slot {
        private final int rackIndex;

        ChipSlot(Container container, int rackIndex) {
            super(container, rackIndex, BenchLayout.chipX(rackIndex), BenchLayout.chipY(rackIndex));
            this.rackIndex = rackIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Optional<ArcadeModule> module = ModuleChips.moduleOf(stack);
            return module.isPresent() && module.get().type() == BenchSlots.typeAt(rackIndex);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !isGhost(rackIndex);
        }
    }

    /** The cartridge tray: a Blank Cartridge (SAVE writes it) or a cartridge (LOAD reads it, SAVE re-saves it). */
    private static final class TraySlot extends Slot {
        TraySlot(Container container) {
            super(container, BenchSlots.TRAY, BenchLayout.TRAY_X, BenchLayout.TRAY_Y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.CARTRIDGE.get()) || stack.is(ModItems.BLANK_CARTRIDGE.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
