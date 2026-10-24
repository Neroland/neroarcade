package za.co.neroland.neroarcade.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.client.exchange.ClientExchange;
import za.co.neroland.neroarcade.client.exchange.ExchangeSlotTips;
import za.co.neroland.neroarcade.client.widget.ArcadeButton;
import za.co.neroland.neroarcade.client.widget.SlotGlyph;
import za.co.neroland.neroarcade.exchange.ExchangeMenu;
import za.co.neroland.neroarcade.exchange.GenreTags;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.network.ExchangeActionPayload;
import za.co.neroland.neroarcade.network.ExchangePagePayload;
import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.registry.ModItems;

/**
 * "Find a game to download" — the Arcade Exchange (004 §8.3) on the family hull:
 *
 * <pre>
 * ┌ Find a game to download ──────────────────────────────── drafts 2 of 8 ┐
 * │ Download a game onto a Blank Cartridge, or publish your own.          │
 * │ [ GAMES ] [ MY DRAFTS ]                                               │
 * │┌ GAMES ──────────────────────────────┐┌ Neon Dodge ────────────┐     │
 * ││▌Neon Dodge            ♥ 12 · 1284 ▶ ││ by Anonymous builder    │     │
 * ││▌Hangar Run            ♥ 3 · 77      ││ [run] [strip] [survive] │     │
 * ││ …                                   ││ ■■■■■ ■■■■■             │     │
 * │└─────────────────────────────────────┘│ [DOWNLOAD] [♥] [UNPUB]  │     │
 * │ [<]   page 1 of 3   [>]               └─────────────────────────┘     │
 * │ tray ▢ [ PUBLISH ]        Inventory ▢▢▢▢▢▢▢▢▢                          │
 * │ ▪ what the last action did, or why not                                │
 * └───────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Every row shows the theme band, the title, hearts and plays; the right pane shows the selected game's credit
 * ("Anonymous builder" unless the author opted in), its genre words in their slots' colours and its ten slots.
 * Every button that cannot act says why on hover; the trough shows the server's answer. CLIENT-ONLY.
 */
public class ExchangeScreen extends AbstractContainerScreen<ExchangeMenu> {

    private static final int W = ExchangeMenu.WIDTH;
    private static final int H = ExchangeMenu.HEIGHT;
    private static final int PAD = 8;
    private static final int LIST_L = 4;
    private static final int LIST_R = 208;
    private static final int LIST_T = 46;
    private static final int LIST_B = 164;
    private static final int PANE_L = 212;
    private static final int PANE_R = 316;
    private static final int TROUGH_T = 222;

    private final List<ArcadeButton> buttons = new ArrayList<>();
    private @Nullable ArcadeButton gamesTab;
    private @Nullable ArcadeButton draftsTab;
    private @Nullable ArcadeButton prev;
    private @Nullable ArcadeButton next;
    private @Nullable ArcadeButton download;
    private @Nullable ArcadeButton heart;
    private @Nullable ArcadeButton unpublish;
    private @Nullable ArcadeButton fetch;
    private @Nullable ArcadeButton publish;
    private int selected = -1;
    private int seenVersion = -1;

    public ExchangeScreen(ExchangeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.inventoryLabelX = ExchangeMenu.INVENTORY_X - 1;
        this.inventoryLabelY = ExchangeMenu.INVENTORY_Y - 11;
    }

    private static void send(ExchangeActionPayload.Op op, int id, int page) {
        Services.NETWORK.sendToServer(new ExchangeActionPayload(op, id, page));
    }

    private @Nullable ExchangePagePayload page() {
        return ClientExchange.page();
    }

    private boolean drafts() {
        ExchangePagePayload p = page();
        return p != null && p.tab() == ExchangePagePayload.TAB_DRAFTS;
    }

    private @Nullable ExchangePagePayload.Row selectedRow() {
        ExchangePagePayload p = page();
        if (p == null || selected < 0 || selected >= p.rows().size()) {
            return null;
        }
        return p.rows().get(selected);
    }

    private ArcadeButton button(int x, int y, int w, String key, Runnable action) {
        ArcadeButton b = addRenderableWidget(new ArcadeButton(leftPos + x, topPos + y, w, 14,
                Component.translatable(key), btn -> action.run()));
        buttons.add(b);
        return b;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        gamesTab = button(LIST_L, 28, 60, "neroarcade.exchange.tab.games", () -> {
            selected = -1;
            send(ExchangeActionPayload.Op.PAGE, 0, 0);
        });
        draftsTab = button(LIST_L + 64, 28, 92, "neroarcade.exchange.tab.drafts", () -> {
            selected = -1;
            send(ExchangeActionPayload.Op.DRAFTS, 0, 0);
        });
        prev = button(LIST_L, LIST_B + 4, 18, "neroarcade.exchange.button.prev", () -> turn(-1));
        next = button(LIST_R - 18, LIST_B + 4, 18, "neroarcade.exchange.button.next", () -> turn(1));
        download = button(PANE_L + 4, 112, PANE_R - PANE_L - 8, "neroarcade.exchange.button.download", () -> act(
                ExchangeActionPayload.Op.DOWNLOAD));
        heart = button(PANE_L + 4, 128, 34, "neroarcade.exchange.button.heart", () -> act(
                ExchangeActionPayload.Op.HEART));
        unpublish = button(PANE_L + 42, 128, PANE_R - PANE_L - 46, "neroarcade.exchange.button.unpublish",
                () -> act(ExchangeActionPayload.Op.UNPUBLISH));
        unpublish.danger(true);
        fetch = button(PANE_L + 4, 112, PANE_R - PANE_L - 8, "neroarcade.exchange.button.fetch", () -> act(
                ExchangeActionPayload.Op.FETCH_DRAFT));
        publish = button(36, ExchangeMenu.TRAY_Y + 1, 106, "neroarcade.exchange.button.publish",
                () -> send(ExchangeActionPayload.Op.PUBLISH, 0, 0));
        gamesTab.setHelp(Component.translatable("neroarcade.exchange.help.games"));
        draftsTab.setHelp(Component.translatable("neroarcade.exchange.help.drafts"));
        download.setHelp(Component.translatable("neroarcade.exchange.help.download"));
        heart.setHelp(Component.translatable("neroarcade.exchange.help.heart"));
        unpublish.setHelp(Component.translatable("neroarcade.exchange.help.unpublish"));
        fetch.setHelp(Component.translatable("neroarcade.exchange.help.fetch"));
        publish.setHelp(Component.translatable("neroarcade.exchange.help.publish"));
        refresh();
    }

    private void turn(int delta) {
        ExchangePagePayload p = page();
        if (p == null) {
            return;
        }
        selected = -1;
        send(drafts() ? ExchangeActionPayload.Op.DRAFTS : ExchangeActionPayload.Op.PAGE, 0, p.page() + delta);
    }

    private void act(ExchangeActionPayload.Op op) {
        ExchangePagePayload.Row row = selectedRow();
        ExchangePagePayload p = page();
        if (row != null && p != null) {
            send(op, row.id(), p.page());
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (seenVersion != ClientExchange.version()) {
            seenVersion = ClientExchange.version();
            ExchangePagePayload p = page();
            if (p == null || selected >= p.rows().size()) {
                selected = -1;
            }
        }
        refresh();
    }

    private void refresh() {
        if (download == null || heart == null || unpublish == null || fetch == null || publish == null
                || prev == null || next == null || gamesTab == null || draftsTab == null) {
            return;
        }
        ExchangePagePayload p = page();
        boolean enabled = p != null && p.enabled();
        Component off = Component.translatable("neroarcade.exchange.deny.disabled");
        Component noRow = Component.translatable("neroarcade.exchange.why.select");
        ExchangePagePayload.Row row = selectedRow();
        boolean tray = menu.tray().getItem(0).is(ModItems.BLANK_CARTRIDGE.get());
        boolean draftTab = drafts();
        download.visible = !draftTab;
        heart.visible = !draftTab;
        unpublish.visible = !draftTab;
        fetch.visible = draftTab;
        set(gamesTab, null);
        set(draftsTab, null);
        set(prev, p != null && p.page() > 0 ? null : Component.translatable("neroarcade.exchange.why.first_page"));
        set(next, p != null && p.page() + 1 < p.pageCount() ? null
                : Component.translatable("neroarcade.exchange.why.last_page"));
        set(download, !enabled ? off : row == null ? noRow : !tray
                ? Component.translatable("neroarcade.exchange.deny.need_blank") : null);
        set(fetch, !enabled ? off : row == null ? noRow : !tray
                ? Component.translatable("neroarcade.exchange.deny.need_blank") : null);
        set(heart, !enabled ? off : row == null ? noRow
                : row.has(ExchangePagePayload.ROW_HEARTED) ? Component.translatable("neroarcade.exchange.deny.already_hearted")
                : row.has(ExchangePagePayload.ROW_MINE) && p != null && !p.moderator()
                        ? Component.translatable("neroarcade.exchange.deny.own_heart") : null);
        set(unpublish, !enabled ? off : row == null ? noRow : !row.has(ExchangePagePayload.ROW_MINE)
                ? Component.translatable("neroarcade.exchange.deny.unpublish_not_yours") : null);
        ItemStack trayStack = menu.tray().getItem(0);
        boolean cartridge = trayStack.is(ModItems.CARTRIDGE.get())
                || (minecraft != null && minecraft.player != null
                        && minecraft.player.getMainHandItem().is(ModItems.CARTRIDGE.get()));
        set(publish, !enabled ? off : !cartridge ? Component.translatable("neroarcade.exchange.deny.need_cartridge")
                : null);
    }

    private static void set(ArcadeButton b, @Nullable Component why) {
        if (why == null) {
            b.enable();
        } else {
            b.disable(why);
        }
    }

    // --- input ----------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int row = rowAt(event.x(), event.y());
        if (row >= 0) {
            selected = row;
            ExchangePagePayload p = page();
            if (p != null && !drafts()) {
                send(ExchangeActionPayload.Op.SELECT, p.rows().get(row).id(), p.page());
            }
            refresh();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int rowHeight() {
        ExchangePagePayload p = page();
        int n = p == null ? 8 : Math.max(1, Math.max(p.rows().size(), 8));
        return Math.max(10, (LIST_B - LIST_T - 4) / n);
    }

    private int rowAt(double mx, double my) {
        ExchangePagePayload p = page();
        if (p == null) {
            return -1;
        }
        double lx = mx - leftPos;
        double ly = my - topPos - LIST_T - 2;
        if (lx < LIST_L + 1 || lx >= LIST_R - 1 || ly < 0) {
            return -1;
        }
        int index = (int) (ly / rowHeight());
        return index < p.rows().size() ? index : -1;
    }

    // --- drawing --------------------------------------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos;
        int y = topPos;
        ExchangePagePayload p = page();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, ArcadeColours.EDGE);
        g.fill(x, y, x + W, y + H, ArcadeColours.INNER);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, ArcadeColours.PANEL);
        g.fill(x + 1, y + 1, x + W - 1, y + 3, ArcadeColours.opaque(ArcadeColours.NEON)); // the kiosk's header bar
        g.text(font, title, x + PAD, y + 6, ArcadeColours.TITLE, false);
        if (p != null) {
            Component used = Component.translatable("neroarcade.exchange.drafts_used", p.draftsUsed(), p.draftsMax());
            g.text(font, used, x + W - PAD - font.width(used), y + 6, ArcadeColours.SUBTLE, false);
        }
        g.text(font, Component.translatable("neroarcade.exchange.subtitle"), x + PAD, y + 17, ArcadeColours.SUBTLE,
                false);
        section(g, x + LIST_L, y + LIST_T - 2, x + LIST_R, y + LIST_B, Component.translatable(drafts()
                ? "neroarcade.exchange.tab.drafts" : "neroarcade.exchange.tab.games"));
        drawRows(g, p, mouseX, mouseY);
        drawPane(g, p, mouseX, mouseY);
        if (p != null) {
            g.centeredText(font, Component.translatable("neroarcade.exchange.page", p.page() + 1, p.pageCount()),
                    x + (LIST_L + LIST_R) / 2, y + LIST_B + 7, ArcadeColours.SUBTLE);
        }
        // slots
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, ArcadeColours.BUTTON_EDGE);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, ArcadeColours.SLOT_FRAME);
        }
        g.text(font, Component.translatable("neroarcade.exchange.tray"), x + ExchangeMenu.TRAY_X - 1,
                y + ExchangeMenu.TRAY_Y - 11, ArcadeColours.SUBTLE, false);
        wrapped(g, Component.translatable("neroarcade.exchange.tray_hint"), x + 14, y + ExchangeMenu.TRAY_Y + 20,
                130, 1, ArcadeColours.SUBTLE);
        // the trough
        int ty = y + TROUGH_T;
        g.fill(x + 1, ty, x + 148, y + H - 1, ArcadeColours.TROUGH);
        g.fill(x + 1, ty, x + 148, ty + 1, ArcadeColours.EDGE);
        Component status = statusLine(p);
        int colour = statusColour(p);
        g.fill(x + PAD, ty + 6, x + PAD + 4, ty + 10, colour);
        wrapped(g, status, x + PAD + 8, ty + 4, 148 - PAD - 12, 3, colour);
        super.extractContents(g, mouseX, mouseY, partialTick);
    }

    private void drawRows(GuiGraphicsExtractor g, @Nullable ExchangePagePayload p, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        if (p == null) {
            g.text(font, Component.translatable("neroarcade.exchange.loading"), x + LIST_L + 6, y + LIST_T + 6,
                    ArcadeColours.SUBTLE, false);
            return;
        }
        if (p.rows().isEmpty()) {
            wrapped(g, Component.translatable(drafts() ? "neroarcade.exchange.empty_drafts"
                    : "neroarcade.exchange.empty"), x + LIST_L + 6, y + LIST_T + 6, LIST_R - LIST_L - 12, 4,
                    ArcadeColours.SUBTLE);
            return;
        }
        int h = rowHeight();
        int hover = rowAt(mouseX, mouseY);
        for (int i = 0; i < p.rows().size(); i++) {
            ExchangePagePayload.Row row = p.rows().get(i);
            int ry = y + LIST_T + 2 + i * h;
            if (i == selected) {
                g.fill(x + LIST_L + 1, ry, x + LIST_R - 1, ry + h - 1, ArcadeColours.SELECTED_ROW);
            } else if (i == hover) {
                g.fill(x + LIST_L + 1, ry, x + LIST_R - 1, ry + h - 1, 0x182FE3FF);
            }
            int theme = row.slotColours()[ModuleType.THEME.ordinal()];
            g.fill(x + LIST_L + 3, ry + 1, x + LIST_L + 6, ry + h - 2, ArcadeColours.opaque(theme));
            Component right = drafts()
                    ? Component.translatable("neroarcade.exchange.row.age", row.ageDays())
                    : Component.translatable("neroarcade.exchange.row.counts", row.hearts(), row.plays());
            int rw = font.width(right);
            g.text(font, right, x + LIST_R - 4 - rw, ry + (h - 8) / 2, ArcadeColours.SUBTLE, false);
            int titleColour = row.has(ExchangePagePayload.ROW_UNREADABLE) ? ArcadeColours.DENY_TEXT
                    : ArcadeColours.TITLE;
            fitted(g, Component.literal(row.title()), x + LIST_L + 9, ry + (h - 8) / 2, LIST_R - LIST_L - 17 - rw,
                    titleColour);
        }
        // an unreadable row says why before Download is pressed
        if (hover >= 0 && p.rows().get(hover).has(ExchangePagePayload.ROW_UNREADABLE)) {
            tooltip(g, List.of(Component.translatable("neroarcade.exchange.deny.unreadable")), mouseX, mouseY);
        }
    }

    private void drawPane(GuiGraphicsExtractor g, @Nullable ExchangePagePayload p, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        ExchangePagePayload.Row row = selectedRow();
        section(g, x + PANE_L, y + LIST_T - 2, x + PANE_R, y + LIST_B, row == null
                ? Component.translatable("neroarcade.exchange.pane") : Component.literal(row.title()));
        int px = x + PANE_L + 5;
        int pw = PANE_R - PANE_L - 10;
        if (row == null) {
            wrapped(g, Component.translatable(p != null && !p.enabled() ? "neroarcade.exchange.disabled_banner"
                    : "neroarcade.exchange.pick"), px, y + LIST_T + 6, pw, 5, ArcadeColours.SUBTLE);
            return;
        }
        int py = y + LIST_T + 5;
        Component by = row.credit().isEmpty() ? Component.translatable("neroarcade.exchange.anonymous")
                : Component.translatable("neroarcade.exchange.by", row.credit());
        if (!drafts()) {
            fitted(g, by, px, py, pw, ArcadeColours.SUBTLE);
            py += 11;
        }
        // genre words, each in its slot's colour
        int tx = px;
        for (String word : row.tags()) {
            Component tag = Component.translatable(GenreTags.langKey(word));
            int tw = font.width(tag) + 4;
            if (tx + tw > px + pw) {
                tx = px;
                py += 11;
            }
            int colour = ArcadeColours.opaque(tagColour(word));
            g.outline(tx, py - 1, tw, 10, colour);
            g.text(font, tag, tx + 2, py, colour, false);
            tx += tw + 2;
        }
        py += 13;
        // the ten slots, 5x2: a dark 10x10 cell with the slot's glyph in its colour (grey = empty, uses the
        // default); hovering a cell names the slot and its chips — never colour alone (004 §2, §8)
        ModuleType[] types = ModuleType.values();
        int hovered = -1;
        for (int i = 0; i < ExchangePagePayload.SLOTS && i < types.length; i++) {
            int sx = px + (i % 5) * SLOT_STEP;
            int sy = py + (i / 5) * SLOT_STEP;
            g.fill(sx, sy, sx + SLOT_CELL, sy + SLOT_CELL, ArcadeColours.SLOT_FRAME);
            int glyph = ExchangeSlotTips.filled(row, i) ? ArcadeColours.opaque(types[i].colour)
                    : ArcadeColours.opaque(ArcadeColours.TRIM);
            SlotGlyph.draw(g, sx + (SLOT_CELL - SlotGlyph.CELLS) / 2, sy + (SLOT_CELL - SlotGlyph.CELLS) / 2,
                    types[i], glyph, 1);
            if (mouseX >= sx && mouseX < sx + SLOT_CELL && mouseY >= sy && mouseY < sy + SLOT_CELL) {
                hovered = i;
            }
        }
        // hearts and plays under the grid, unless two lines of genre words pushed it into the buttons
        int countsY = py + 26;
        if (!drafts() && countsY + 9 <= y + COUNTS_LIMIT) {
            fitted(g, Component.translatable("neroarcade.exchange.row.counts", row.hearts(), row.plays()), px,
                    countsY, pw, ArcadeColours.SUBTLE);
        }
        if (hovered >= 0) {
            tooltip(g, slotTooltip(row, hovered), mouseX, mouseY);
        }
    }

    /** A hover tooltip, each line wrapped at {@link #TOOLTIP_WIDTH} (styles kept). */
    private void tooltip(GuiGraphicsExtractor g, List<Component> lines, int mx, int my) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) {
            out.addAll(font.split(line, TOOLTIP_WIDTH));
        }
        g.setTooltipForNextFrame(font, out, mx, my);
    }

    private static final int TOOLTIP_WIDTH = 200;

    /** A slot cell's 10x10 size and its grid step. */
    private static final int SLOT_CELL = 10;
    private static final int SLOT_STEP = 12;
    /** The pane's buttons start at y 112; the counts line must end above them. */
    private static final int COUNTS_LIMIT = 111;

    /** Line 1 the slot word in its colour; line 2 its chips' names, or "Empty — the game uses the default". */
    private static List<Component> slotTooltip(ExchangePagePayload.Row row, int index) {
        ModuleType type = ModuleType.values()[index];
        List<Component> lines = new ArrayList<>(2);
        lines.add(Component.translatable(ExchangeSlotTips.slotKey(index)).withColor(type.colour));
        boolean empty = row.chipsIn(index).isEmpty();
        MutableComponent names = Component.empty();
        List<String> keys = ExchangeSlotTips.chipKeys(row, index);
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                names.append(Component.literal(" + "));
            }
            names.append(Component.translatable(keys.get(i)));
        }
        lines.add(names.withColor(empty ? ArcadeColours.TEXT_SUBTLE : ArcadeColours.TEXT_LIGHT));
        return lines;
    }

    /** A tag word's colour: the slot of the chip that carries it (looked up in the chip registry), else grey. */
    private static int tagColour(String word) {
        for (ArcadeModule module : ModuleRegistry.all()) {
            if (word.equals(module.genreTag())) {
                return module.type().colour;
            }
        }
        return 0x8A93A6;
    }

    private Component statusLine(@Nullable ExchangePagePayload p) {
        if (p == null) {
            return Component.translatable("neroarcade.exchange.loading");
        }
        if (!p.statusKey().isEmpty()) {
            return Component.translatable(p.statusKey(), p.statusArgs().toArray());
        }
        if (!p.enabled()) {
            return Component.translatable("neroarcade.exchange.deny.disabled");
        }
        return Component.translatable(drafts() ? "neroarcade.exchange.hint_drafts" : "neroarcade.exchange.hint");
    }

    private static int statusColour(@Nullable ExchangePagePayload p) {
        if (p == null) {
            return ArcadeColours.SUBTLE;
        }
        return switch (p.statusKind()) {
            case ExchangePagePayload.STATUS_OK -> ArcadeColours.OK_TEXT;
            case ExchangePagePayload.STATUS_DENY -> ArcadeColours.DENY_TEXT;
            case ExchangePagePayload.STATUS_PENDING -> ArcadeColours.AMBER_TEXT;
            default -> p.enabled() ? ArcadeColours.SUBTLE : ArcadeColours.DENY_TEXT;
        };
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcadeColours.SUBTLE, false);
    }

    // --- helpers --------------------------------------------------------------------------------------

    private void section(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, Component caption) {
        g.outline(x0, y0, x1 - x0, y1 - y0, ArcadeColours.EDGE);
        int w = Math.min(font.width(caption), x1 - x0 - 12);
        g.fill(x0 + 4, y0, x0 + 8 + w, y0 + 1, ArcadeColours.PANEL);
        fitted(g, caption, x0 + 6, y0 - 4, x1 - x0 - 12, ArcadeColours.SUBTLE);
    }

    private void fitted(GuiGraphicsExtractor g, Component text, int x, int y, int width, int colour) {
        if (width <= 0) {
            return;
        }
        if (font.width(text) <= width) {
            g.text(font, text, x, y, colour, false);
            return;
        }
        String ellipsis = "…";
        net.minecraft.network.chat.FormattedText cut = font.substrByWidth(text,
                Math.max(0, width - font.width(ellipsis)));
        g.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(
                net.minecraft.network.chat.FormattedText.composite(cut,
                        net.minecraft.network.chat.FormattedText.of(ellipsis))), x, y, colour, false);
    }

    private void wrapped(GuiGraphicsExtractor g, Component text, int x, int y, int width, int maxLines, int colour) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(8, width));
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            g.text(font, lines.get(i), x, y + i * 10, colour, false);
        }
    }
}
