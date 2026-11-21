package za.co.neroland.neroarcade.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.jetbrains.annotations.Nullable;

/**
 * The gauge hover tooltips for NeroArcade screens: each gauge names itself, says how much, and gives its rate
 * (progress also gives the time left, or why it is not moving). Builders append lines to a list; the screen
 * emits them through {@link #show}. Numbers come from {@link GaugeMath}; every word is an English source lang
 * key. Machine-scoped numbers already on screen only — no player identities, no coordinates.
 */
public final class GaugeTooltips {

    private GaugeTooltips() {
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("neroarcade.gui.gauge." + key, args);
    }

    /**
     * Progress: the gauge's name, percent, time left at the current speed, and the stall cause when one is
     * given. When not working, the cause (or "Idle") stands in for the numbers.
     */
    public static void progress(List<Component> out, Component name, boolean working, int permille,
            double permillePerTick, @Nullable Component stallCause) {
        out.add(name);
        if (!working) {
            out.add(stallCause != null ? stallCause : tr("progress.idle"));
            return;
        }
        out.add(tr("progress.percent", Math.max(0, Math.min(100, Math.round(permille / 10F)))));
        if (stallCause != null) {
            out.add(stallCause);
            return;
        }
        long ticks = GaugeMath.ticksRemaining(permille, permillePerTick);
        out.add(ticks >= 0 ? tr("progress.left", GaugeMath.duration(ticks)) : tr("progress.estimating"));
    }

    /** The wrap width for a screen {@code screenWidth} wide: half the window, 100..220 px. */
    public static int wrapWidth(int screenWidth) {
        return Math.max(100, Math.min(220, screenWidth / 2));
    }

    /**
     * Emit a tooltip at the mouse, every line wrapped to {@link #wrapWidth} so a long cause never runs off a small
     * window (deferred on 1.21.6+, drawn immediately before that).
     */
    public static void show(GuiGraphicsExtractor g, Font font, List<Component> lines, int screenWidth, int mouseX,
            int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        int width = wrapWidth(screenWidth);
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) {
            out.addAll(font.split(line, width));
        }
        //? if >=1.21.6 {
        g.setTooltipForNextFrame(font, out, mouseX, mouseY);
        //?} else {
        /*g.renderTooltip(font, out, mouseX, mouseY);
        *///?}
    }
}
