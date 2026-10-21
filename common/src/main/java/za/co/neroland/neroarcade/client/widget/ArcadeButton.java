package za.co.neroland.neroarcade.client.widget;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.client.ArcadeColours;

/**
 * The NeroArcade button (the family's hull button, as in NeroSecurity): a dark body, an edge that
 * brightens on hover or focus, a 1-px top sheen, centred text; {@link #danger} draws a red edge. The "no manual
 * needed" rule: <b>a disabled button always says why</b> ({@link #disable(Component)} — the reason is its hover
 * tooltip); an enabled one says what it does ({@link #setHelp}). CLIENT-ONLY.
 */
public class ArcadeButton extends AbstractWidget {

    private final Consumer<ArcadeButton> onPress;
    private boolean danger;
    private @Nullable Component why;
    private @Nullable Component help;
    private @Nullable Component shownTip;

    public ArcadeButton(int x, int y, int width, int height, Component label, Consumer<ArcadeButton> onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
    }

    public ArcadeButton danger(boolean value) {
        this.danger = value;
        return this;
    }

    /** Enables the button (the reason, if any, is dropped). */
    public void enable() {
        this.active = true;
        this.why = null;
        sync();
    }

    /** Disables the button; {@code reason} (one plain sentence) becomes its tooltip. */
    public void disable(Component reason) {
        this.active = false;
        this.why = reason;
        sync();
    }

    public void setHelp(@Nullable Component value) {
        this.help = value;
        sync();
    }

    /** The current disabled reason, or null. */
    public @Nullable Component why() {
        return active ? null : why;
    }

    private void sync() {
        Component tip = active ? help : why;
        if (!Objects.equals(tip, shownTip)) {
            shownTip = tip;
            setTooltip(tip == null ? null : Tooltip.create(tip));
        }
    }

    public void press() {
        if (this.active && this.visible) {
            onPress.accept(this);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        press();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.active && this.visible && event.isSelection()) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            press();
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = this.active && isHoveredOrFocused();
        int edge;
        int body;
        if (!this.active) {
            edge = ArcadeColours.DISABLED_EDGE;
            body = ArcadeColours.DISABLED_BODY;
        } else if (danger) {
            edge = ArcadeColours.DANGER_EDGE;
            body = hot ? ArcadeColours.BUTTON_HOVER : ArcadeColours.BUTTON_BODY;
        } else {
            edge = hot ? ArcadeColours.EDGE_HOT : ArcadeColours.BUTTON_EDGE;
            body = hot ? ArcadeColours.BUTTON_HOVER : ArcadeColours.BUTTON_BODY;
        }
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
        int text = !this.active ? ArcadeColours.DISABLED_TEXT : ArcadeColours.TITLE;
        Font font = Minecraft.getInstance().font;
        Component label = getMessage();
        int lw = font.width(label);
        g.text(font, label, x + (w - lw) / 2, y + (h - 8) / 2, text, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
