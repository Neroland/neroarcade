package za.co.neroland.neroarcade.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.client.widget.ArcadeButton;
import za.co.neroland.neroarcade.duplicator.DuplicatorMenu;
import za.co.neroland.neroarcade.duplicator.DuplicatorRules;
import za.co.neroland.neroarcade.registry.ModItems;

/**
 * "Copy a game" — the Cartridge Duplicator (004 §8.4) on the family hull: the original and blank slots with their
 * labels, a cyan progress bar, {@code [COPY]}, the two output slots, and one sentence that says what happened or why
 * not ("Made 1 copy — credit kept"). A disabled {@code [COPY]} says why on hover. CLIENT-ONLY.
 */
public class DuplicatorScreen extends AbstractContainerScreen<DuplicatorMenu> {

    private static final int W = DuplicatorMenu.WIDTH;
    private static final int H = DuplicatorMenu.HEIGHT;
    private static final int PAD = 8;
    private static final int BAR_X = 50;
    private static final int BAR_Y = 49;
    private static final int BAR_W = 58;
    /** The status line's first row; the second sits 10 px below, the inventory starts at 96. */
    static final int STATUS_Y = 76;

    private @Nullable ArcadeButton copyButton;

    public DuplicatorScreen(DuplicatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.inventoryLabelX = DuplicatorMenu.INVENTORY_X - 1;
        this.inventoryLabelY = DuplicatorMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        copyButton = addRenderableWidget(new ArcadeButton(leftPos + BAR_X, topPos + 58, BAR_W, 16,
                Component.translatable("neroarcade.duplicator.button.copy"), b -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DuplicatorMenu.BUTTON_COPY);
                    }
                }));
        copyButton.setHelp(Component.translatable("neroarcade.duplicator.help.copy"));
        refresh();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    /** The reason COPY cannot start, read from the synced slots (the server re-checks). */
    private @Nullable Component whyNot() {
        ItemStack original = menu.container().getItem(DuplicatorMenu.SLOT_ORIGINAL);
        ItemStack blank = menu.container().getItem(DuplicatorMenu.SLOT_BLANK);
        if (menu.copying()) {
            return Component.translatable(DuplicatorRules.Deny.BUSY.langKey());
        }
        if (!original.is(ModItems.CARTRIDGE.get())) {
            return Component.translatable(DuplicatorRules.Deny.NEED_ORIGINAL.langKey());
        }
        if (!blank.is(ModItems.BLANK_CARTRIDGE.get())) {
            return Component.translatable(DuplicatorRules.Deny.NEED_BLANK.langKey());
        }
        if (!menu.container().getItem(DuplicatorMenu.SLOT_OUT_ORIGINAL).isEmpty()
                || !menu.container().getItem(DuplicatorMenu.SLOT_OUT_COPY).isEmpty()) {
            return Component.translatable(DuplicatorRules.Deny.OUTPUT_FULL.langKey());
        }
        return null;
    }

    private void refresh() {
        if (copyButton == null) {
            return;
        }
        Component why = whyNot();
        if (why == null) {
            copyButton.enable();
        } else {
            copyButton.disable(why);
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos;
        int y = topPos;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, ArcadeColours.EDGE);
        g.fill(x, y, x + W, y + H, ArcadeColours.INNER);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, ArcadeColours.PANEL);
        g.text(font, title, x + PAD, y + 6, ArcadeColours.TITLE, false);
        g.text(font, Component.translatable("neroarcade.duplicator.subtitle"), x + PAD, y + 17, ArcadeColours.SUBTLE,
                false);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, ArcadeColours.BUTTON_EDGE);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, ArcadeColours.SLOT_FRAME);
        }
        g.text(font, Component.translatable("neroarcade.duplicator.original"), x + DuplicatorMenu.ORIGINAL_X + 20,
                y + DuplicatorMenu.ORIGINAL_Y + 5, ArcadeColours.AMBER_TEXT, false);
        g.text(font, Component.translatable("neroarcade.duplicator.blank"), x + DuplicatorMenu.BLANK_X + 20,
                y + DuplicatorMenu.BLANK_Y + 5, ArcadeColours.SUBTLE, false);
        g.text(font, Component.translatable("neroarcade.duplicator.out"), x + DuplicatorMenu.OUT_X,
                y + DuplicatorMenu.OUT_Y - 11, ArcadeColours.SUBTLE, false);
        // progress: a 6-px cyan bar, full width = one copy
        int bx = x + BAR_X;
        int by = y + BAR_Y;
        g.fill(bx, by, bx + BAR_W, by + 6, ArcadeColours.SLIDER_TROUGH);
        int filled = Math.min(BAR_W, menu.progress() * BAR_W / DuplicatorRules.COPY_TICKS);
        if (filled > 0) {
            g.fill(bx, by, bx + filled, by + 6, ArcadeColours.CYAN_TEXT);
        } else if (menu.status() == DuplicatorMenu.STATUS_DONE) {
            g.fill(bx, by, bx + BAR_W, by + 6, ArcadeColours.OK_TEXT);
        }
        super.extractContents(g, mouseX, mouseY, partialTick);
        // the result line: up to two lines at y 76 and 86, clear of the inventory at 96 (no "Inventory" label)
        Component line = statusLine();
        int colour = statusColour();
        List<FormattedCharSequence> wrapped = font.split(line, W - 2 * PAD);
        for (int i = 0; i < wrapped.size() && i < 2; i++) {
            g.text(font, wrapped.get(i), x + PAD, y + STATUS_Y + i * 10, colour, false);
        }
    }

    private Component statusLine() {
        int status = menu.status();
        if (menu.copying()) {
            return Component.translatable("neroarcade.duplicator.status.copying");
        }
        if (status == DuplicatorMenu.STATUS_DONE) {
            return Component.translatable("neroarcade.duplicator.status.done");
        }
        if (status >= DuplicatorMenu.STATUS_DENY_BASE) {
            int ordinal = status - DuplicatorMenu.STATUS_DENY_BASE;
            DuplicatorRules.Deny[] all = DuplicatorRules.Deny.values();
            if (ordinal >= 0 && ordinal < all.length) {
                return Component.translatable(all[ordinal].langKey());
            }
        }
        Component why = whyNot();
        return why != null ? why : Component.translatable("neroarcade.duplicator.status.ready");
    }

    private int statusColour() {
        int status = menu.status();
        if (menu.copying()) {
            return ArcadeColours.CYAN_TEXT;
        }
        if (status == DuplicatorMenu.STATUS_DONE) {
            return ArcadeColours.OK_TEXT;
        }
        return status >= DuplicatorMenu.STATUS_DENY_BASE ? ArcadeColours.DENY_TEXT : ArcadeColours.SUBTLE;
    }

    /**
     * Draws nothing: the title and the slot captions already say what the slots are, and the status line's two rows
     * use the space the "Inventory" label had (Stage 5 review F2).
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }
}
