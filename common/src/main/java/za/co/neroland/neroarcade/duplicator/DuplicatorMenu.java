package za.co.neroland.neroarcade.duplicator;

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

import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.registry.ModMenus;

/**
 * "Copy a game" — the Cartridge Duplicator menu (003 §7): slot 0 the original, slot 1 Blank Cartridges, slots 2 and
 * 3 the two identical cartridges a copy leaves (take-only), then the player inventory. {@code [COPY]} is
 * {@code clickMenuButton(player, 0)}; no packet of its own. Data: {@link #DATA_PROGRESS} (0..60),
 * {@link #DATA_STATUS} (0 nothing to say, 1 "made a copy", {@code 100 + Deny.ordinal()} the last refusal). Nothing
 * can be taken from or put into the duplicator while it copies.
 *
 * <p><b>Menu slot indices:</b> 0 original, 1 blank, 2 output (original), 3 output (copy), 4..30 inventory,
 * 31..39 hotbar.
 */
public class DuplicatorMenu extends AbstractContainerMenu {

    public static final int SLOT_ORIGINAL = 0;
    public static final int SLOT_BLANK = 1;
    public static final int SLOT_OUT_ORIGINAL = 2;
    public static final int SLOT_OUT_COPY = 3;
    public static final int SIZE = 4;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_STATUS = 1;
    public static final int DATA_COUNT = 2;

    public static final int STATUS_NONE = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_DENY_BASE = 100;

    public static final int BUTTON_COPY = 0;

    public static final int WIDTH = 176;
    public static final int HEIGHT = 178;
    public static final int ORIGINAL_X = 26;
    public static final int ORIGINAL_Y = 30;
    public static final int BLANK_X = 26;
    public static final int BLANK_Y = 56;
    public static final int OUT_X = 116;
    public static final int OUT_Y = 43;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 96;

    private final Container container;
    private final ContainerData data;
    private final @Nullable CartridgeDuplicatorBlockEntity duplicator;

    /** Client-side reconstruction (the {@code MenuType} factory). */
    public DuplicatorMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(SIZE), new SimpleContainerData(DATA_COUNT), null);
    }

    /** Server side, bound to a duplicator. */
    public DuplicatorMenu(int containerId, Inventory playerInventory, CartridgeDuplicatorBlockEntity duplicator) {
        this(containerId, playerInventory, duplicator.inventory(), duplicator.dataAccess(), duplicator);
    }

    private DuplicatorMenu(int containerId, Inventory playerInventory, Container container, ContainerData data,
            @Nullable CartridgeDuplicatorBlockEntity duplicator) {
        super(ModMenus.CARTRIDGE_DUPLICATOR.get(), containerId);
        checkContainerSize(container, SIZE);
        checkContainerDataCount(data, DATA_COUNT);
        this.container = container;
        this.data = data;
        this.duplicator = duplicator;
        addSlot(new InputSlot(container, SLOT_ORIGINAL, ORIGINAL_X, ORIGINAL_Y, true));
        addSlot(new InputSlot(container, SLOT_BLANK, BLANK_X, BLANK_Y, false));
        addSlot(new OutputSlot(container, SLOT_OUT_ORIGINAL, OUT_X, OUT_Y));
        addSlot(new OutputSlot(container, SLOT_OUT_COPY, OUT_X + 22, OUT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    public Container container() {
        return container;
    }

    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    public int status() {
        return data.get(DATA_STATUS);
    }

    public boolean copying() {
        return progress() > 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_COPY || duplicator == null) {
            return false;
        }
        duplicator.startCopy();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return duplicator == null || Container.stillValidBlockEntity(duplicator, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < SIZE) {
            if (!moveItemStackTo(stack, SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (slots.get(SLOT_ORIGINAL).mayPlace(stack)) {
            if (!moveItemStackTo(stack, SLOT_ORIGINAL, SLOT_ORIGINAL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slots.get(SLOT_BLANK).mayPlace(stack)) {
            if (!moveItemStackTo(stack, SLOT_BLANK, SLOT_BLANK + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    /** The original (one written cartridge) or the blank slot (Blank Cartridges); locked while copying. */
    private final class InputSlot extends Slot {
        private final boolean original;

        InputSlot(Container container, int index, int x, int y, boolean original) {
            super(container, index, x, y);
            this.original = original;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (copying()) {
                return false;
            }
            return original ? stack.is(ModItems.CARTRIDGE.get()) : stack.is(ModItems.BLANK_CARTRIDGE.get());
        }

        @Override
        public boolean mayPickup(Player player) {
            return !copying();
        }

        @Override
        public int getMaxStackSize() {
            return original ? 1 : 64;
        }
    }

    /** A finished cartridge: take only. */
    private static final class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
