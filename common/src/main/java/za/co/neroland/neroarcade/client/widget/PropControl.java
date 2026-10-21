package za.co.neroland.neroarcade.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.PropertySpec;

/**
 * One chip property as a control — never a text box (004 §8): INT is a slider over the spec's range (6-px
 * {@code #0b0f1a} trough, fill in the slot colour, value on the right; click or drag, mouse wheel steps by one),
 * BOOL a toggle (on/off), ENUM a cycle button (left click next, right click previous). A change is committed
 * (sent to the server as {@code SET_PROP}) on release, toggle or cycle; the control shows the pending value until
 * the server's next draft arrives. CLIENT-ONLY.
 */
public class PropControl extends AbstractWidget {

    /** Receives the committed value: INT value, BOOL 0/1, ENUM constant index. */
    @FunctionalInterface
    public interface Commit {
        void commit(PropertySpec spec, int value);
    }

    private final PropertySpec spec;
    private final ArcadeModule owner;
    private final int colour;
    private final Commit commit;
    private int value;
    private boolean dragging;

    public PropControl(int x, int y, int width, int height, ArcadeModule owner, PropertySpec spec, int value,
            int colour, Commit commit) {
        super(x, y, width, height, Component.translatable(spec.langKey(owner)));
        this.owner = owner;
        this.spec = spec;
        this.value = value;
        this.colour = colour;
        this.commit = commit;
        setTooltip(Tooltip.create(Component.translatable("neroarcade.bench.help.prop." + spec.kind().name()
                .toLowerCase(java.util.Locale.ROOT), Component.translatable(spec.langKey(owner)))));
    }

    public PropertySpec spec() {
        return spec;
    }

    public int value() {
        return value;
    }

    /** The server's value (ignored while the player is dragging). */
    public void setValue(int fromServer) {
        if (!dragging) {
            value = fromServer;
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        switch (spec.kind()) {
            case INT -> {
                dragging = true;
                value = valueAt(event.x());
            }
            case BOOL -> {
                value = value == 0 ? 1 : 0;
                commit.commit(spec, value);
            }
            case ENUM -> {
                int n = spec.constants().size();
                value = Math.floorMod(value + (event.button() == 1 ? -1 : 1), n);
                commit.commit(spec, value);
            }
        }
    }

    @Override
    protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo buttonInfo) {
        return buttonInfo.button() == 0 || (spec.kind() == za.co.neroland.neroarcade.module.PropertyKind.ENUM
                && buttonInfo.button() == 1);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            value = valueAt(event.x());
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            commit.commit(spec, value);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!this.active || !isMouseOver(x, y) || spec.kind() != za.co.neroland.neroarcade.module.PropertyKind.INT
                || scrollY == 0) {
            return false;
        }
        int next = Math.max(spec.min(), Math.min(spec.max(), value + (scrollY > 0 ? 1 : -1)));
        if (next != value) {
            value = next;
            commit.commit(spec, value);
        }
        return true;
    }

    private int trackX() {
        return getX();
    }

    private int trackWidth() {
        return Math.max(10, getWidth() - 30);
    }

    private int valueAt(double mouseX) {
        int span = spec.max() - spec.min();
        if (span <= 0) {
            return spec.min();
        }
        double t = (mouseX - trackX()) / trackWidth();
        t = Math.max(0, Math.min(1, t));
        return spec.min() + (int) Math.round(t * span);
    }

    /** What the control reads as ("60", "on", "Light maze"). */
    public Component valueText() {
        return switch (spec.kind()) {
            case INT -> Component.literal(Integer.toString(value));
            case BOOL -> Component.translatable(value != 0 ? "neroarcade.value.on" : "neroarcade.value.off");
            case ENUM -> Component.translatable(spec.constantLangKey(owner,
                    spec.constants().get(Math.floorMod(value, spec.constants().size()))));
        };
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = this.active && isHoveredOrFocused();
        int fill = ArcadeColours.opaque(colour);
        switch (spec.kind()) {
            case INT -> {
                int tw = trackWidth();
                int ty = y + (h - 6) / 2;
                g.fill(x, ty, x + tw, ty + 6, hot ? ArcadeColours.EDGE_HOT : ArcadeColours.BUTTON_EDGE);
                g.fill(x + 1, ty + 1, x + tw - 1, ty + 5, ArcadeColours.SLIDER_TROUGH);
                int span = Math.max(1, spec.max() - spec.min());
                int filled = (tw - 2) * (value - spec.min()) / span;
                g.fill(x + 1, ty + 1, x + 1 + filled, ty + 5, fill);
                g.fill(x + 1 + filled - 1, ty - 1, x + 1 + filled + 1, ty + 7, ArcadeColours.TITLE);
                Component v = valueText();
                g.text(font, v, x + w - font.width(v), y + (h - 8) / 2, ArcadeColours.TITLE, false);
            }
            case BOOL, ENUM -> {
                g.fill(x, y, x + w, y + h, hot ? ArcadeColours.EDGE_HOT : ArcadeColours.BUTTON_EDGE);
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? ArcadeColours.BUTTON_HOVER : ArcadeColours.BUTTON_BODY);
                if (spec.kind() == za.co.neroland.neroarcade.module.PropertyKind.BOOL && value != 0) {
                    g.fill(x + 2, y + 2, x + 6, y + h - 2, fill);
                }
                Component v = valueText();
                Component shown = spec.kind() == za.co.neroland.neroarcade.module.PropertyKind.ENUM
                        ? Component.literal("< ").append(v).append(" >") : v;
                g.text(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, ArcadeColours.TITLE, false);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
