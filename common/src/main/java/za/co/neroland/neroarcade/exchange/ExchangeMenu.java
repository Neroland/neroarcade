package za.co.neroland.neroarcade.exchange;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.network.ExchangePagePayload;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.registry.ModMenus;

/**
 * The Arcade Exchange menu: a one-slot download tray of the viewer's own (a Blank Cartridge to download or fetch
 * onto, or a written cartridge to publish), then the player inventory. The tray belongs to this menu, not to the
 * kiosk: two players at one kiosk never share it, and whatever is in it goes back to the player when the menu
 * closes, like the vanilla enchanting table (review 003b F6). Listing pages travel as {@code ExchangePagePayload};
 * actions as {@code ExchangeActionPayload}, accepted only while this menu is the sender's open menu. The viewer's
 * tab, page and last status live here, server side, for as long as the menu is open.
 *
 * <p><b>Menu slot indices:</b> 0 tray, 1..27 player inventory, 28..36 hotbar.
 */
public class ExchangeMenu extends AbstractContainerMenu {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 262;
    public static final int TRAY_X = 14;
    public static final int TRAY_Y = 196;
    public static final int INVENTORY_X = 152;
    public static final int INVENTORY_Y = 178;

    private final Container tray;
    private final @Nullable ArcadeExchangeBlockEntity kiosk;

    private int tab = ExchangePagePayload.TAB_LISTINGS;
    private int page;
    private ExchangePages.Status status = ExchangePages.Status.NONE;

    /** Client-side reconstruction (the {@code MenuType} factory). */
    public ExchangeMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, newTray(), null);
    }

    /** Server side, bound to a kiosk; the tray is this viewer's own. */
    public ExchangeMenu(int containerId, Inventory playerInventory, ArcadeExchangeBlockEntity kiosk) {
        this(containerId, playerInventory, newTray(), kiosk);
    }

    /** A fresh, per-menu one-slot tray. */
    static SimpleContainer newTray() {
        return new SimpleContainer(1);
    }

    private ExchangeMenu(int containerId, Inventory playerInventory, Container tray,
            @Nullable ArcadeExchangeBlockEntity kiosk) {
        super(ModMenus.ARCADE_EXCHANGE.get(), containerId);
        checkContainerSize(tray, 1);
        this.tray = tray;
        this.kiosk = kiosk;
        addSlot(new TraySlot(tray));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    public Container tray() {
        return tray;
    }

    public @Nullable ArcadeExchangeBlockEntity kiosk() {
        return kiosk;
    }

    public int tab() {
        return tab;
    }

    public int page() {
        return page;
    }

    public ExchangePages.Status status() {
        return status;
    }

    void view(int newTab, int newPage) {
        this.tab = newTab;
        this.page = Math.max(0, newPage);
    }

    void status(ExchangePages.Status value) {
        this.status = value == null ? ExchangePages.Status.NONE : value;
    }

    /** The tray's item goes back to the player (or drops at their feet), never into the kiosk. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (kiosk != null) {
            clearContainer(player, tray);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return kiosk == null || Container.stillValidBlockEntity(kiosk, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!slots.get(0).mayPlace(stack) || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    /** The tray: one Blank Cartridge or one cartridge. */
    private static final class TraySlot extends Slot {
        TraySlot(Container container) {
            super(container, 0, TRAY_X, TRAY_Y);
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
