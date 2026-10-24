package za.co.neroland.neroarcade.client.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import za.co.neroland.neroarcade.client.bench.BenchModel;
import za.co.neroland.neroarcade.module.ModuleType;

/**
 * A slot's 5x5 glyph (004 §2), drawn at {@code pxPerCell} pixels per cell from its top-left corner — the mark the
 * bench rack rows and the Exchange pane's slot cells share, so a slot is never colour alone. CLIENT-ONLY.
 */
public final class SlotGlyph {

    /** The glyph's size in cells. */
    public static final int CELLS = 5;

    private SlotGlyph() {
    }

    /** Draws {@code type}'s glyph at {@code (x, y)} in {@code argb}, {@code pxPerCell} pixels per lit cell. */
    public static void draw(GuiGraphicsExtractor g, int x, int y, ModuleType type, int argb, int pxPerCell) {
        for (int cy = 0; cy < CELLS; cy++) {
            for (int cx = 0; cx < CELLS; cx++) {
                if (BenchModel.glyph(type, cx, cy)) {
                    int gx = x + cx * pxPerCell;
                    int gy = y + cy * pxPerCell;
                    g.fill(gx, gy, gx + pxPerCell, gy + pxPerCell, argb);
                }
            }
        }
    }
}
