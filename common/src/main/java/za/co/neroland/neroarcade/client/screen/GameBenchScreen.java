package za.co.neroland.neroarcade.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.bench.BenchDraft;
import za.co.neroland.neroarcade.bench.BenchLayout;
import za.co.neroland.neroarcade.bench.BenchSlots;
import za.co.neroland.neroarcade.bench.GameBenchMenu;
import za.co.neroland.neroarcade.bench.TitleRules;
import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.client.ArcadeClientConfig;
import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.client.bench.BenchModel;
import za.co.neroland.neroarcade.client.bench.BenchPreview;
import za.co.neroland.neroarcade.client.bench.ClientBenchState;
import za.co.neroland.neroarcade.client.hud.ArcadeHud;
import za.co.neroland.neroarcade.client.input.ArcadeKeys;
import za.co.neroland.neroarcade.client.match.ClientMatch;
import za.co.neroland.neroarcade.client.match.ClientMatches;
import za.co.neroland.neroarcade.client.render.CabinetScreens;
import za.co.neroland.neroarcade.client.render.ScreenAssets;
import za.co.neroland.neroarcade.client.render.ScreenTexture;
import za.co.neroland.neroarcade.client.widget.ArcadeButton;
import za.co.neroland.neroarcade.client.widget.PropControl;
import za.co.neroland.neroarcade.client.widget.SlotGlyph;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.ModuleChips;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.BenchWarning;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.network.BenchEditPayload;
import za.co.neroland.neroarcade.network.BenchStatePayload;
import za.co.neroland.neroarcade.network.LeaveSeatPayload;
import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.registry.ModSounds;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * "Build your game" — the Game Bench (004 §8.1), on the NeroSecurity procedural hull (panel, 1-px edge, a
 * sentence title, a subtitle that says what the screen controls, and a feedback trough that explains every refusal
 * in a sentence). Designed to need no manual:
 *
 * <pre>
 * ┌ Build your game ─────────────────────────────── cartridge in tray: Blank Cartridge ┐
 * │ Drop chips in the rack. Empty slots use the default shown in grey.                 │
 * │┌ RACK ─────────────────────────────────────┐┌ PREVIEW ──────────────────────────┐ │
 * ││▣ Arena    [ ]  A 16 by 16 board with a wall…││ ┌──────┐ Title [Neon Dodge     ]   │ │
 * ││▣ Pawn     [ ]  A free cursor that moves in…││ │64x64 │ [tray] Blank Cartridge to │ │
 * ││▣ Rule     [ ]  Empty — uses Survive        ││ │ live │        save, cartridge to │ │
 * ││▣ Hazard   [ ][+][+] ▲ Nothing can hurt the…││ └──────┘        load               │ │
 * ││▣ Pickup   [+][+][+] Empty — nothing to col…││ Live demo — restarts on each change │ │
 * ││▣ Control  [ ]  Up, down, left and right …  │└───────────────────────────────────┘ │
 * ││▣ Pace     [ ]  Empty — uses Steady         │┌ PARAMETERS — Rule: Survive ───────┐ │
 * ││▣ Modifier [ ]  Empty — no twist            ││ Seconds  ▓▓▓▓▓▓░░░░░░░░░░  60     │ │
 * ││▣ Multi…   [ ]  Empty — uses Solo        [✕]││ Lives    ▓░░░░░░░░░░░░░░░  1      │ │
 * ││▣ Theme    [ ]  Bright neon shapes on a da… │└───────────────────────────────────┘ │
 * │└───────────────────────────────────────────┘  Inventory                            │
 * │ [ TEST PLAY ] [ SAVE TO CARTRIDGE ] [ LOAD ]   ▢▢▢▢▢▢▢▢▢                            │
 * │ credit: off (Anonymous builder)                ▢▢▢▢▢▢▢▢▢                            │
 * │ ▪ status: what the last action did, or why not ▢▢▢▢▢▢▢▢▢ (hotbar)                   │
 * └────────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Each row shows the slot's 5x5 glyph and word in the slot colour (never colour alone), its chip slot(s) — stack
 * slots show {@code +} where another chip fits — and one line: the chip's effect, or the grey empty-slot sentence,
 * or (grey, with a ▲) the bench warning that belongs to that row. Hover a row for the slot's purpose; click a row
 * to edit its chip's properties with sliders, toggles and cycle buttons (no text box except the title). Chips
 * loaded from a cartridge (LOAD) have an amber dashed frame, a tooltip, and a ✕ CLEAR button on the row. Every
 * disabled button explains itself on hover in one sentence.
 *
 * <p><b>Test play.</b> TEST PLAY runs the draft as a solo match on this bench while the screen stays open: the
 * preview pane shows the live match, the arcade keys steer it, and closing the screen (Esc) stops it —
 * {@code STOP_TEST} is sent before the menu closes ({@link #onClose}), with {@code LeaveSeat} as the fallback for any
 * other way the screen goes away ({@link #removed}). CLIENT-ONLY.
 */
public class GameBenchScreen extends AbstractContainerScreen<GameBenchMenu> {

    public static final int W = BenchLayout.WIDTH;
    public static final int H = BenchLayout.HEIGHT;
    private static final int PAD = 8;
    private static final int RACK_L = 4;
    private static final int RACK_R = 256;
    private static final int COL_L = 262;
    private static final int COL_R = 436;
    private static final int PREVIEW_X = 266;
    private static final int PREVIEW_Y = 33;
    private static final int PARAMS_TOP = 114;
    private static final int PARAMS_BOTTOM = 170;
    private static final int BUTTONS_Y = 216;
    private static final int TROUGH_TOP = 248;
    private static final int TITLE_SEND_DELAY = 10;
    private static final int TOOLTIP_WIDTH = 220;

    private final BenchPreview preview = new BenchPreview();
    private final ScreenRenderer previewRenderer = new ScreenRenderer(
            ScreenAssets.sheets(ThemePalettes.Theme.NEON_VOID), ScreenAssets.font());
    private final ScreenTexture previewTexture = new ScreenTexture(
            Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "dynamic/bench_preview"));
    private final List<PropControl> propControls = new ArrayList<>();
    private final ArcadeButton[] clearButtons = new ArcadeButton[ModuleType.values().length];

    private @Nullable EditBox titleBox;
    private @Nullable ArcadeButton testButton;
    private @Nullable ArcadeButton saveButton;
    private @Nullable ArcadeButton loadButton;
    private ModuleType selected = ModuleType.ARENA;
    private int selectedStack;
    private boolean selectionChosen;
    private long seenVersion = -1;
    private String lastStatus = "";
    private int titleSendIn = -1;
    private boolean stopSent;
    private int ticks;
    private String builtPropsFor = "";

    public GameBenchScreen(GameBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.inventoryLabelX = BenchLayout.INVENTORY_X - 1;
        this.inventoryLabelY = BenchLayout.INVENTORY_Y - 11;
    }

    // --- state helpers ------------------------------------------------------------------------------

    private @Nullable BenchStatePayload state() {
        return ClientBenchState.state();
    }

    private @Nullable BlockPos benchPos() {
        BenchStatePayload s = state();
        return s == null ? null : s.bench();
    }

    private boolean testing() {
        BenchStatePayload s = state();
        return (s != null && s.testing()) || menu.testing();
    }

    private void send(BenchEditPayload.Op op, ModuleType type, int stack, String prop, int intValue, boolean bool,
            String title) {
        BlockPos pos = benchPos();
        if (pos != null) {
            Services.NETWORK.sendToServer(new BenchEditPayload(pos, op, type.ordinal(), stack, prop, intValue, bool,
                    title));
        }
    }

    private void action(BenchEditPayload.Op op) {
        BlockPos pos = benchPos();
        if (pos != null) {
            Services.NETWORK.sendToServer(BenchEditPayload.action(pos, op));
        }
    }

    /** The module in a rack slot (from the synced container), if any. */
    private Optional<ArcadeModule> moduleAt(int rackIndex) {
        return ModuleChips.moduleOf(menu.container().getItem(rackIndex));
    }

    /** The resolved game's property values for the chip at a rack slot (stack slots are compacted). */
    private @Nullable PropertyValues propsAt(ModuleType type, int stack) {
        ResolvedGame game = ClientBenchState.resolved();
        if (game == null) {
            return null;
        }
        int listIndex = 0;
        for (int s = 0; s < stack; s++) {
            if (moduleAt(BenchSlots.index(type, s)).isPresent()) {
                listIndex++;
            }
        }
        List<? extends ArcadeModule> modules = game.modules(type);
        Optional<ArcadeModule> here = moduleAt(BenchSlots.index(type, stack));
        if (listIndex >= modules.size() || here.isEmpty() || !modules.get(listIndex).id().equals(here.get().id())) {
            return null;
        }
        return game.props(type, listIndex);
    }

    private BenchModel.Context context() {
        BenchStatePayload s = state();
        ItemStack tray = menu.container().getItem(BenchSlots.TRAY);
        boolean canSave = s != null ? s.canSave() : !tray.isEmpty();
        String typed = titleBox == null ? "" : titleBox.getValue().trim();
        return new BenchModel.Context(testing(), canSave, tray.is(ModItems.CARTRIDGE.get()), typed.isEmpty(),
                ClientBenchState.refused() != null);
    }

    // --- lifecycle ----------------------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        EditBox box = new EditBox(font, x + 364, y + 38, COL_R - 368, 14, Component.translatable(
                "neroarcade.bench.title_label"));
        box.setMaxLength(TitleRules.MAX_LENGTH);
        box.setHint(Component.translatable("neroarcade.bench.title_hint"));
        box.setValue(currentTitle());
        box.setResponder(text -> titleSendIn = TITLE_SEND_DELAY);
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("neroarcade.bench.help.title", TitleRules.MAX_LENGTH)));
        titleBox = addRenderableWidget(box);

        testButton = addRenderableWidget(new ArcadeButton(x + RACK_L, y + BUTTONS_Y, 66, 16,
                Component.translatable("neroarcade.bench.button.test_play"), b -> pressTest()));
        saveButton = addRenderableWidget(new ArcadeButton(x + RACK_L + 70, y + BUTTONS_Y, 104, 16,
                Component.translatable("neroarcade.bench.button.save"), b -> {
                    flushTitle();
                    action(BenchEditPayload.Op.SAVE);
                }));
        loadButton = addRenderableWidget(new ArcadeButton(x + RACK_L + 178, y + BUTTONS_Y, 50, 16,
                Component.translatable("neroarcade.bench.button.load"), b -> action(BenchEditPayload.Op.LOAD)));
        for (ModuleType type : ModuleType.values()) {
            int rowTop = y + BenchLayout.rowTop(type.ordinal());
            ArcadeButton clear = new ArcadeButton(x + RACK_R - 14, rowTop + 3, 12, 12, Component.literal("✕"),
                    b -> clearGhosts(type)).danger(true);
            clear.setHelp(Component.translatable(BenchModel.helpKey(BenchModel.Button.CLEAR)));
            clear.visible = false;
            clearButtons[type.ordinal()] = addRenderableWidget(clear);
        }
        builtPropsFor = "";
        refresh();
    }

    private String currentTitle() {
        BenchStatePayload s = state();
        if (s == null) {
            return "";
        }
        String title = s.draft().title();
        return BenchDraft.PLACEHOLDER_TITLE.equals(title) ? "" : title;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        long version = ClientBenchState.version();
        if (version != seenVersion) {
            seenVersion = version;
            onState();
        }
        if (titleSendIn > 0 && --titleSendIn == 0) {
            flushTitle();
        }
        updatePreview();
        refresh();
    }

    /** A new draft from the server: restart the preview, follow the title, report the status. */
    private void onState() {
        BenchStatePayload s = state();
        preview.setGame(ClientBenchState.resolved());
        if (s == null) {
            return;
        }
        if (titleBox != null && !titleBox.isFocused() && titleSendIn < 0) {
            titleBox.setValue(currentTitle());
        }
        if (!s.statusKey().equals(lastStatus)) {
            lastStatus = s.statusKey();
            if (!lastStatus.isEmpty() && !BenchModel.isSuccess(lastStatus)) {
                ArcadeClientSound.deny();
            }
        }
        if (s.testing() && titleBox != null && titleBox.isFocused()) {
            setFocused(null);
        }
        builtPropsFor = "";
    }

    private void flushTitle() {
        titleSendIn = -1;
        if (titleBox == null) {
            return;
        }
        String typed = titleBox.getValue().trim();
        if (!typed.isEmpty() && !typed.equals(currentTitle())) {
            send(BenchEditPayload.Op.SET_TITLE, ModuleType.ARENA, 0, "", 0, false, typed);
        }
    }

    private void pressTest() {
        if (testing()) {
            action(BenchEditPayload.Op.STOP_TEST);
        } else {
            flushTitle();
            setFocused(null);
            action(BenchEditPayload.Op.TEST_PLAY);
        }
    }

    private void clearGhosts(ModuleType type) {
        for (int s = 0; s < type.maxStack; s++) {
            if (menu.isGhost(BenchSlots.index(type, s))) {
                send(BenchEditPayload.Op.CLEAR_SLOT, type, s, "", 0, false, "");
            }
        }
    }

    private void updatePreview() {
        boolean focused = minecraft != null && minecraft.isWindowActive();
        ScreenRenderer r = previewRenderer;
        BlockPos pos = benchPos();
        ClientMatch live = pos == null ? null : ClientMatches.get(pos);
        if (testing() && live != null && live.latest() != null) {
            r.use(ScreenAssets.sheets(live.spriteMap().theme()), ScreenAssets.font());
            CabinetScreens.drawMatch(r, live, ticks);
            previewTexture.upload(r, live.spriteMap().theme(), ArcadeClientConfig.scanlines());
            return;
        }
        ValidationError refused = ClientBenchState.refused();
        if (refused != null) {
            r.use(ScreenAssets.sheets(ThemePalettes.Theme.NEON_VOID), ScreenAssets.font());
            r.drawStatus(ScreenStatus.UNREADABLE, CabinetScreens.reason(refused.code.name()), ticks);
            previewTexture.upload(r, ThemePalettes.Theme.NEON_VOID, ArcadeClientConfig.scanlines());
            return;
        }
        if (focused) {
            preview.advance(1); // one step per client tick; never while the game window is in the background
        }
        MatchState demo = preview.state();
        ThemePalettes.Theme theme = preview.spriteMap().theme();
        r.use(ScreenAssets.sheets(theme), ScreenAssets.font());
        if (demo == null) {
            r.drawStatus(ScreenStatus.NO_CARTRIDGE, "", ticks);
        } else {
            r.drawMatch(demo, preview.spriteMap(), EntityPositions.AUTHORITATIVE, ticks);
        }
        previewTexture.upload(r, theme, ArcadeClientConfig.scanlines());
    }

    /** Mirrors the draft into the buttons, the CLEAR buttons and the property controls. */
    private void refresh() {
        BenchModel.Context c = context();
        boolean noBench = benchPos() == null;
        if (testButton != null) {
            boolean stop = c.testing();
            testButton.setMessage(Component.translatable(stop ? "neroarcade.bench.button.stop_test"
                    : "neroarcade.bench.button.test_play"));
            testButton.danger(stop);
            apply(testButton, stop ? BenchModel.Button.STOP_TEST : BenchModel.Button.TEST_PLAY, c, noBench);
        }
        if (saveButton != null) {
            apply(saveButton, BenchModel.Button.SAVE, c, noBench);
        }
        if (loadButton != null) {
            apply(loadButton, BenchModel.Button.LOAD, c, noBench);
        }
        for (ModuleType type : ModuleType.values()) {
            ArcadeButton clear = clearButtons[type.ordinal()];
            if (clear == null) {
                continue;
            }
            boolean ghost = false;
            for (int s = 0; s < type.maxStack; s++) {
                ghost |= menu.isGhost(BenchSlots.index(type, s));
            }
            clear.visible = ghost;
            String why = BenchModel.whyDisabled(BenchModel.Button.CLEAR, c);
            if (why == null) {
                clear.enable();
            } else {
                clear.disable(Component.translatable(why));
            }
        }
        chooseDefaultSelection();
        rebuildProps(c.testing());
    }

    private static void apply(ArcadeButton button, BenchModel.Button kind, BenchModel.Context c, boolean noBench) {
        String why = noBench ? "neroarcade.bench.why.waiting" : BenchModel.whyDisabled(kind, c);
        if (why == null) {
            button.enable();
            button.setHelp(kind == BenchModel.Button.TEST_PLAY
                    ? Component.translatable(BenchModel.helpKey(kind), (Object[]) testKeyNames())
                    : Component.translatable(BenchModel.helpKey(kind)));
        } else {
            button.disable(Component.translatable(why));
        }
    }

    private void chooseDefaultSelection() {
        if (selectionChosen) {
            return;
        }
        for (ModuleType type : ModuleType.values()) {
            for (int s = 0; s < type.maxStack; s++) {
                if (moduleAt(BenchSlots.index(type, s)).isPresent()) {
                    selected = type;
                    selectedStack = s;
                    return;
                }
            }
        }
    }

    /** Rebuilds the PARAMETERS controls when the selection, the chip or the draft changed. */
    private void rebuildProps(boolean testing) {
        int rack = BenchSlots.index(selected, selectedStack);
        Optional<ArcadeModule> module = rack < 0 ? Optional.empty() : moduleAt(rack);
        String key = selected + ":" + selectedStack + ":" + module.map(m -> m.id().toString()).orElse("-") + ":"
                + seenVersion + ":" + testing;
        if (key.equals(builtPropsFor)) {
            return;
        }
        for (PropControl control : propControls) {
            if (control.isDragging()) {
                return; // 005a P4: never pull a slider out from under the mouse; rebuild after the release
            }
        }
        builtPropsFor = key;
        for (PropControl control : propControls) {
            removeWidget(control);
        }
        propControls.clear();
        if (module.isEmpty()) {
            return;
        }
        ArcadeModule chip = module.get();
        PropertyValues values = propsAt(selected, selectedStack);
        int row = 0;
        int colour = selected.colour;
        ModuleType type = selected;
        int stack = selectedStack;
        for (PropertySpec spec : chip.properties()) {
            if (row >= 4) {
                break;
            }
            int value = valueOf(spec, values);
            PropControl control = new PropControl(leftPos + COL_L + 62, topPos + PARAMS_TOP + 12 + row * 11, 104, 10,
                    chip, spec, value, colour, (s, v) -> send(BenchEditPayload.Op.SET_PROP, type, stack, s.name(), v,
                            v != 0, ""));
            if (testing) {
                control.disableWith(Component.translatable("neroarcade.bench.why.testing"));
            }
            propControls.add(addRenderableWidget(control));
            row++;
        }
    }

    private static int valueOf(PropertySpec spec, @Nullable PropertyValues values) {
        try {
            return switch (spec.kind()) {
                case INT -> values == null ? ((za.co.neroland.neroarcade.module.PropertyValue.IntValue)
                        spec.defaultValue()).value() : values.getInt(spec.name());
                case BOOL -> values == null ? (((za.co.neroland.neroarcade.module.PropertyValue.BoolValue)
                        spec.defaultValue()).value() ? 1 : 0) : (values.getBool(spec.name()) ? 1 : 0);
                case ENUM -> Math.max(0, spec.constants().indexOf(values == null
                        ? ((za.co.neroland.neroarcade.module.PropertyValue.EnumValue) spec.defaultValue()).constant()
                        : values.getEnum(spec.name())));
            };
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public void onClose() {
        flushTitle();
        if (testing() && !stopSent) {
            stopSent = true;
            action(BenchEditPayload.Op.STOP_TEST); // while the menu is still open, so the server accepts it
        }
        super.onClose();
    }

    @Override
    public void removed() {
        if (testing() && !stopSent) {
            BlockPos pos = benchPos();
            if (pos != null) {
                Services.NETWORK.sendToServer(new LeaveSeatPayload(pos)); // the menu may already be closed
            }
        }
        ArcadeKeys.releaseScreenKeys();
        previewTexture.close();
        super.removed();
    }

    // --- input --------------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (testing() && !(getFocused() instanceof EditBox) && ArcadeKeys.screenKey(event, true)) {
            return true; // the test play has the arcade keys
        }
        if (getFocused() instanceof EditBox box && box.canConsumeInput() && !event.isEscape() && !event.isCycleFocus()) {
            if (event.isSelection()) {
                flushTitle();
                setFocused(null);
                return true;
            }
            box.keyPressed(event);
            return true; // typing "e" never closes the screen
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (ArcadeKeys.screenKey(event, false)) {
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ModuleType row = rowAt(event.x(), event.y());
        if (row != null) {
            // a click anywhere on a row (its chip slot included) selects that chip for PARAMETERS
            selected = row;
            selectedStack = stackAt(row, event.x());
            selectionChosen = true;
        }
        return super.mouseClicked(event, doubleClick) || row != null;
    }

    private @Nullable ModuleType rowAt(double mx, double my) {
        double lx = mx - leftPos;
        double ly = my - topPos;
        if (lx < RACK_L || lx >= RACK_R) {
            return null;
        }
        int index = (int) Math.floor((ly - BenchLayout.ROWS_TOP) / BenchLayout.RACK_ROW);
        if (ly < BenchLayout.ROWS_TOP || index < 0 || index >= ModuleType.values().length) {
            return null;
        }
        return ModuleType.values()[index];
    }

    /** The stack position under the mouse (stack slots), else the first filled one, else 0. */
    private int stackAt(ModuleType type, double mx) {
        double lx = mx - leftPos;
        for (int s = 0; s < type.maxStack; s++) {
            int sx = BenchLayout.RACK_X + s * BenchLayout.STACK_STEP;
            if (lx >= sx - 1 && lx < sx + 17) {
                return s;
            }
        }
        for (int s = 0; s < type.maxStack; s++) {
            if (moduleAt(BenchSlots.index(type, s)).isPresent()) {
                return s;
            }
        }
        return 0;
    }

    // --- drawing ------------------------------------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g, mouseX, mouseY);
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawOverlays(g, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, ArcadeColours.EDGE);
        g.fill(x, y, x + W, y + H, ArcadeColours.INNER);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, ArcadeColours.PANEL);
        g.text(font, Component.translatable("container.neroarcade.game_bench"), x + PAD, y + 6, ArcadeColours.TITLE,
                false);
        Component tray = trayLine();
        g.text(font, tray, x + W - PAD - font.width(tray), y + 6, ArcadeColours.SUBTLE, false);
        g.text(font, Component.translatable("neroarcade.bench.subtitle"), x + PAD, y + 17, ArcadeColours.SUBTLE, false);

        section(g, x + RACK_L, y + 26, x + RACK_R, y + 212, Component.translatable("neroarcade.bench.rack"));
        section(g, x + COL_L, y + 26, x + COL_R, y + 110, Component.translatable("neroarcade.bench.preview"));
        section(g, x + COL_L, y + PARAMS_TOP, x + COL_R, y + PARAMS_BOTTOM, paramsCaption());
        drawRows(g, mouseX, mouseY);
        drawPreview(g);
        drawParams(g);

        // slot frames: every active menu slot; LOAD-materialised (ghost) chips get an amber dashed frame
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, ArcadeColours.BUTTON_EDGE);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, ArcadeColours.SLOT_FRAME);
            int index = slot.index;
            if (BenchSlots.isChipSlot(index) && menu.isGhost(index)) {
                dashed(g, sx, sy, 18, ArcadeColours.AMBER_TEXT);
            } else if (BenchSlots.isChipSlot(index) && !slot.hasItem() && BenchSlots.typeAt(index).stacks()) {
                g.centeredText(font, "+", sx + 9, sy + 5, ArcadeColours.DISABLED_TEXT);
            }
        }
        g.text(font, Component.translatable("neroarcade.bench.tray"), x + BenchLayout.TRAY_X - 1,
                y + BenchLayout.TRAY_Y - 11, ArcadeColours.SUBTLE, false);
        int hintX = x + BenchLayout.TRAY_X + 20;
        drawWrapped(g, Component.translatable("neroarcade.bench.tray_hint"), hintX, y + BenchLayout.TRAY_Y - 1,
                x + COL_R - 4 - hintX, 3, ArcadeColours.SUBTLE);
        g.text(font, Component.translatable("neroarcade.bench.title_label"), x + 338, y + 41, ArcadeColours.SUBTLE,
                false);
        g.text(font, Component.translatable("neroarcade.bench.credit_off"), x + PAD, y + 236, ArcadeColours.SUBTLE,
                false);

        // the trough: the last action's result, else the first warning, else a hint
        int ty = y + TROUGH_TOP;
        g.fill(x + 1, ty, x + RACK_R, y + H - 1, ArcadeColours.TROUGH);
        g.fill(x + 1, ty, x + RACK_R, ty + 1, ArcadeColours.EDGE);
        Component status = statusLine();
        int colour = statusColour();
        g.fill(x + PAD, ty + 5, x + PAD + 4, ty + 9, colour);
        fitted(g, status, x + PAD + 8, ty + 3, RACK_R - PAD - 12, colour);
        explain(g, x + PAD, ty + 1, RACK_R - PAD, 13, status, mouseX, mouseY);
    }

    /** The bound names of the arcade keys a test play uses: up, left, down, right, A (the player's own bindings). */
    private static Component[] testKeyNames() {
        return new Component[] {ArcadeKeys.UP.getTranslatedKeyMessage(), ArcadeKeys.LEFT.getTranslatedKeyMessage(),
                ArcadeKeys.DOWN.getTranslatedKeyMessage(), ArcadeKeys.RIGHT.getTranslatedKeyMessage(),
                ArcadeKeys.BUTTON_A.getTranslatedKeyMessage()};
    }

    private Component trayLine() {
        ItemStack tray = menu.container().getItem(BenchSlots.TRAY);
        if (tray.isEmpty()) {
            return Component.translatable("neroarcade.bench.tray_empty");
        }
        return Component.translatable("neroarcade.bench.tray_holds", tray.getHoverName());
    }

    private Component paramsCaption() {
        int rack = BenchSlots.index(selected, selectedStack);
        Optional<ArcadeModule> module = rack < 0 ? Optional.empty() : moduleAt(rack);
        MutableComponent caption = Component.translatable("neroarcade.bench.parameters").append(" — ")
                .append(Component.translatable(selected.langKey()));
        module.ifPresent(m -> caption.append(": ").append(Component.translatable(m.langKey())));
        return caption;
    }

    private void drawRows(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        BenchStatePayload s = state();
        List<BenchWarning> warnings = s == null ? List.of() : s.warnings();
        for (ModuleType type : ModuleType.values()) {
            int rowTop = topPos + BenchLayout.rowTop(type.ordinal());
            int x = leftPos + RACK_L + 2;
            if (type == selected) {
                g.fill(leftPos + RACK_L + 1, rowTop, leftPos + RACK_R - 1, rowTop + BenchLayout.RACK_ROW,
                        ArcadeColours.SELECTED_ROW);
            }
            // glyph: the slot's 5x5 mark at 2 px per cell on a dark square, in the slot colour
            int gx = x + 1;
            int gy = rowTop + 3;
            g.fill(gx, gy, gx + 12, gy + 12, ArcadeColours.SLOT_FRAME);
            int colour = ArcadeColours.opaque(type.colour);
            SlotGlyph.draw(g, gx + 1, gy + 1, type, colour, 2);
            Component word = Component.translatable(type.langKey());
            fitted(g, word, x + 16, rowTop + 5, 60, colour);

            int textX = leftPos + BenchLayout.RACK_X + type.maxStack * BenchLayout.STACK_STEP + 3;
            boolean ghost = clearButtons[type.ordinal()] != null && clearButtons[type.ordinal()].visible;
            int textRight = leftPos + RACK_R - (ghost ? 18 : 4);
            BenchWarning warning = null;
            for (BenchWarning w : warnings) {
                if (BenchModel.rowOf(w) == type) {
                    warning = w;
                    break;
                }
            }
            Component line;
            int lineColour;
            if (warning != null) {
                line = Component.literal("▲ ").append(Component.translatable(warning.langKey()));
                lineColour = ArcadeColours.SUBTLE;
            } else {
                Component effect = effectLine(type);
                line = effect;
                lineColour = filled(type) ? ArcadeColours.TITLE : ArcadeColours.SUBTLE;
            }
            fitted(g, line, textX, rowTop + 5, textRight - textX, lineColour);
            if (mouseX >= leftPos + RACK_L && mouseX < textRight && mouseY >= rowTop
                    && mouseY < rowTop + BenchLayout.RACK_ROW && hoveredSlot == null) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable(type.langKey()).withColor(type.colour));
                tip.add(Component.translatable(type.descLangKey()));
                tip.add(effectLine(type));
                Optional<ArcadeModule> only = singleChip(type);
                only.ifPresent(m -> tip.add(Component.translatable(m.descLangKey())));
                if (warning != null) {
                    tip.add(Component.translatable(warning.langKey()));
                }
                if (ghost) {
                    tip.add(Component.translatable("neroarcade.bench.ghost"));
                }
                tip.add(Component.translatable("neroarcade.bench.help.row"));
                tooltip(g, tip, mouseX, mouseY);
            }
        }
    }

    /** The chip of a row that holds exactly one. */
    private Optional<ArcadeModule> singleChip(ModuleType type) {
        Optional<ArcadeModule> found = Optional.empty();
        for (int s = 0; s < type.maxStack; s++) {
            Optional<ArcadeModule> here = moduleAt(BenchSlots.index(type, s));
            if (here.isPresent()) {
                if (found.isPresent()) {
                    return Optional.empty();
                }
                found = here;
            }
        }
        return found;
    }

    private boolean filled(ModuleType type) {
        for (int s = 0; s < type.maxStack; s++) {
            if (moduleAt(BenchSlots.index(type, s)).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** The row's one line: the chip's effect, the chips of a stack by name, or the empty-slot sentence. */
    private Component effectLine(ModuleType type) {
        List<ArcadeModule> chips = new ArrayList<>();
        for (int s = 0; s < type.maxStack; s++) {
            moduleAt(BenchSlots.index(type, s)).ifPresent(chips::add);
        }
        if (chips.size() == 1) {
            ArcadeModule chip = chips.get(0);
            int stack = 0;
            for (int s = 0; s < type.maxStack; s++) {
                if (moduleAt(BenchSlots.index(type, s)).isPresent()) {
                    stack = s;
                    break;
                }
            }
            PropertyValues values = propsAt(type, stack);
            return Component.translatable(BenchModel.effectKey(chip),
                    chip.effectArgs(values != null ? values : PropertyValues.defaults(chip)));
        }
        if (chips.size() > 1) {
            // 005a P11: "Falling block + 1 more" reads better than a truncated comma list
            return Component.translatable("neroarcade.bench.row_more", Component.translatable(chips.get(0).langKey()),
                    chips.size() - 1);
        }
        Optional<String> defaultName = BenchModel.defaultName(type);
        return defaultName.isPresent()
                ? Component.translatable(BenchModel.emptyKey(type), Component.translatable(defaultName.get()))
                : Component.translatable(BenchModel.emptyKey(type));
    }

    private void drawPreview(GuiGraphicsExtractor g) {
        int px = leftPos + PREVIEW_X;
        int py = topPos + PREVIEW_Y;
        g.fill(px - 1, py - 1, px + 65, py + 65, ArcadeColours.BUTTON_EDGE);
        if (previewTexture.ready()) {
            g.blit(previewTexture.id(), px, py, px + 64, py + 64, 0f, 1f, 0f, 1f);
        } else {
            g.fill(px, py, px + 64, py + 64, ArcadeColours.INNER);
        }
        Component caption;
        int colour = ArcadeColours.SUBTLE;
        BlockPos pos = benchPos();
        ClientMatch live = pos == null ? null : ClientMatches.get(pos);
        if (testing() && live != null) {
            MatchState state = live.latest();
            int score = state == null ? 0 : state.score(Math.max(0, Math.min(state.seatCount() - 1, live.mySeat())));
            String clock = state == null || state.timerTicks() <= 0 ? "" : "  " + ArcadeHud.clock(state.timerTicks());
            caption = Component.translatable("neroarcade.bench.testing_caption", score, clock);
            colour = ArcadeColours.CYAN_TEXT;
            Component keys = countdownKeys();
            if (keys != null && font.width(keys) <= captionWidth()) {
                caption = keys; // otherwise the wider trough carries it (statusLine)
            } else if (live.busPhase() == BusPhase.RESULTS) {
                caption = Component.translatable("neroarcade.bench.testing_over",
                        ArcadeKeys.BUTTON_A.getTranslatedKeyMessage());
            }
        } else {
            caption = Component.translatable("neroarcade.bench.preview_caption");
        }
        fitted(g, caption, leftPos + PREVIEW_X, topPos + 100, captionWidth(), colour);
    }

    private static int captionWidth() {
        return COL_R - PREVIEW_X - 4;
    }

    /**
     * While a test play waits or counts down: "W A S D move · Space action · ESC closes" with the player's own
     * bindings (the HUD legend is hidden while a screen is open), else null.
     */
    private @Nullable Component countdownKeys() {
        BlockPos pos = benchPos();
        ClientMatch live = pos == null ? null : ClientMatches.get(pos);
        if (!testing() || live == null || (live.busPhase() != BusPhase.LOBBY && live.busPhase() != BusPhase.COUNTDOWN)) {
            return null;
        }
        return Component.translatable(BenchModel.TESTING_KEYS, (Object[]) testKeyNames());
    }

    private void drawParams(GuiGraphicsExtractor g) {
        int rack = BenchSlots.index(selected, selectedStack);
        Optional<ArcadeModule> module = rack < 0 ? Optional.empty() : moduleAt(rack);
        int x = leftPos + COL_L + 6;
        int y = topPos + PARAMS_TOP + 8;
        if (module.isEmpty()) {
            Component empty = BenchModel.defaultName(selected).isPresent()
                    ? Component.translatable("neroarcade.bench.params_empty_default",
                            Component.translatable(selected.langKey()),
                            Component.translatable(BenchModel.defaultName(selected).get()))
                    : Component.translatable("neroarcade.bench.params_empty", Component.translatable(selected.langKey()));
            drawWrapped(g, empty, x, y + 4, COL_R - COL_L - 12, 4, ArcadeColours.SUBTLE);
            return;
        }
        if (module.get().properties().isEmpty()) {
            drawWrapped(g, Component.translatable("neroarcade.bench.params_none"), x, y + 4, COL_R - COL_L - 12, 4,
                    ArcadeColours.SUBTLE);
            return;
        }
        for (PropControl control : propControls) {
            fitted(g, control.getMessage(), x, control.getY() + 1, 56, ArcadeColours.TITLE);
        }
        if (module.get().properties().size() > propControls.size()) {
            g.text(font, Component.translatable("neroarcade.bench.params_more",
                    module.get().properties().size() - propControls.size()), x, topPos + PARAMS_BOTTOM - 10,
                    ArcadeColours.SUBTLE, false);
        }
    }

    private void drawOverlays(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (hoveredSlot != null && BenchSlots.isChipSlot(hoveredSlot.index) && menu.isGhost(hoveredSlot.index)
                && menu.getCarried().isEmpty()) {
            tooltip(g, List.of(Component.translatable("neroarcade.bench.ghost_tip")), mouseX, mouseY);
        }
        if (hoveredSlot != null && hoveredSlot.index == BenchSlots.TRAY && !hoveredSlot.hasItem()
                && menu.getCarried().isEmpty()) {
            tooltip(g, List.of(Component.translatable("neroarcade.bench.tray_hint")), mouseX, mouseY);
        }
        if (hoveredSlot != null && BenchSlots.isChipSlot(hoveredSlot.index) && !hoveredSlot.hasItem()
                && menu.getCarried().isEmpty()) {
            ModuleType type = BenchSlots.typeAt(hoveredSlot.index);
            tooltip(g, List.of(Component.translatable("neroarcade.bench.slot_tip", Component.translatable(type.langKey()))),
                    mouseX, mouseY);
        }
        // the credit line's opt-in hint (review 005a P1, Stage 6)
        int creditX = leftPos + PAD;
        int creditY = topPos + 236;
        int creditW = font.width(Component.translatable("neroarcade.bench.credit_off"));
        if (mouseX >= creditX && mouseX < creditX + creditW && mouseY >= creditY && mouseY < creditY + 9) {
            tooltip(g, List.of(Component.translatable("neroarcade.bench.credit_hint")), mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcadeColours.SUBTLE, false);
    }

    private Component statusLine() {
        BenchStatePayload s = state();
        if (s == null) {
            return Component.translatable("neroarcade.bench.status.waiting");
        }
        Component keys = countdownKeys();
        if (keys != null && font.width(keys) > captionWidth()
                && (s.statusKey().isEmpty() || s.statusKey().equals("neroarcade.bench.status.test_started"))) {
            return keys; // too wide for the preview caption at this GUI scale: the trough says it instead
        }
        if (!s.statusKey().isEmpty()) {
            return Component.translatable(s.statusKey(), s.statusArgs().toArray());
        }
        ValidationError refused = ClientBenchState.refused();
        if (refused != null) {
            return Component.translatable(refused.langKey, refused.args.toArray());
        }
        for (BenchWarning warning : s.warnings()) {
            if (BenchModel.rowOf(warning) == null) {
                return Component.translatable(warning.langKey());
            }
        }
        return Component.translatable("neroarcade.bench.status.hint");
    }

    private int statusColour() {
        BenchStatePayload s = state();
        if (s == null) {
            return ArcadeColours.SUBTLE;
        }
        if (!s.statusKey().isEmpty()) {
            return BenchModel.isSuccess(s.statusKey()) ? ArcadeColours.OK_TEXT : ArcadeColours.DENY_TEXT;
        }
        return ClientBenchState.refused() != null ? ArcadeColours.DENY_TEXT : ArcadeColours.SUBTLE;
    }

    // --- small drawing helpers ----------------------------------------------------------------------

    private void section(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, Component caption) {
        g.outline(x0, y0, x1 - x0, y1 - y0, ArcadeColours.EDGE);
        int w = Math.min(font.width(caption), x1 - x0 - 12);
        g.fill(x0 + 4, y0, x0 + 8 + w, y0 + 1, ArcadeColours.PANEL);
        fitted(g, caption, x0 + 6, y0 - 4, x1 - x0 - 12, ArcadeColours.SUBTLE);
    }

    private static void dashed(GuiGraphicsExtractor g, int x, int y, int size, int colour) {
        for (int i = 0; i < size; i += 3) {
            int len = Math.min(2, size - i);
            g.fill(x + i, y, x + i + len, y + 1, colour);
            g.fill(x + i, y + size - 1, x + i + len, y + size, colour);
            g.fill(x, y + i, x + 1, y + i + len, colour);
            g.fill(x + size - 1, y + i, x + size, y + i + len, colour);
        }
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
        net.minecraft.network.chat.FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width(ellipsis)));
        g.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(
                net.minecraft.network.chat.FormattedText.composite(cut,
                        net.minecraft.network.chat.FormattedText.of(ellipsis))), x, y, colour, false);
    }

    private void drawWrapped(GuiGraphicsExtractor g, Component text, int x, int y, int width, int maxLines,
            int colour) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(8, width));
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            g.text(font, lines.get(i), x, y + i * 10, colour, false);
        }
    }

    private void explain(GuiGraphicsExtractor g, int x, int y, int w, int h, Component text, int mx, int my) {
        if (mx >= x && mx < x + w && my >= y && my < y + h && font.width(text) > w - 12) {
            tooltip(g, List.of(text), mx, my);
        }
    }

    private void tooltip(GuiGraphicsExtractor g, List<Component> lines, int mx, int my) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) {
            out.addAll(font.split(line, TOOLTIP_WIDTH));
        }
        g.setTooltipForNextFrame(font, out, mx, my);
    }

    /** The deny buzz for a refused bench action (client side; the status bar says why). */
    static final class ArcadeClientSound {
        private ArcadeClientSound() {
        }

        static void deny() {
            try {
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ModSounds.CABINET_DENY.get(),
                                1.0F, 0.6F));
            } catch (RuntimeException e) {
                // never let a sound break the screen
            }
        }
    }
}
