package za.co.neroland.neroarcade.client.render;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.client.screen.ArcadeFont;
import za.co.neroland.neroarcade.client.screen.SpriteSheets;
import za.co.neroland.neroarcade.client.screen.ThemePalettes;

/**
 * Loads {@code textures/screen/**} as standalone images through the resource manager — <b>never through the block
 * atlas</b> (004 Registry ids) — and turns them into index sheets once per theme. A missing file logs once and
 * draws as fills (tiles) or nothing (sprites, text). Cleared on resource reload and disconnect. CLIENT-ONLY.
 */
public final class ScreenAssets {

    private static final Map<ThemePalettes.Theme, SpriteSheets> SHEETS = new EnumMap<>(ThemePalettes.Theme.class);
    private static @Nullable ArcadeFont font;

    private ScreenAssets() {
    }

    public static synchronized SpriteSheets sheets(ThemePalettes.Theme theme) {
        return SHEETS.computeIfAbsent(theme, ScreenAssets::load);
    }

    public static synchronized ArcadeFont font() {
        ArcadeFont current = font;
        if (current == null) {
            int[] argb = read(id("textures/screen/font/arcade5x7.png"), ArcadeFont.SHEET_W, ArcadeFont.SHEET_H);
            current = argb == null ? ArcadeFont.blank() : ArcadeFont.fromArgb(argb);
            font = current;
        }
        return current;
    }

    /** Forget everything (resource reload, disconnect). */
    public static synchronized void clear() {
        SHEETS.clear();
        font = null;
    }

    private static SpriteSheets load(ThemePalettes.Theme theme) {
        int[][] argb = new int[SpriteSheets.Sheet.values().length][];
        for (SpriteSheets.Sheet sheet : SpriteSheets.Sheet.values()) {
            argb[sheet.ordinal()] = read(id("textures/screen/" + theme.folder() + "/" + sheet.file + ".png"),
                    sheet.width, SpriteSheets.SPRITE);
        }
        return SpriteSheets.fromArgb(theme, argb);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, path);
    }

    /** The image's ARGB pixels, or null when missing or the wrong size. */
    private static int @Nullable [] read(Identifier location, int width, int height) {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] screen texture {} is missing", location);
                return null;
            }
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                if (image.getWidth() != width || image.getHeight() != height) {
                    NeroArcadeCommon.LOGGER.warn("[NeroArcade] screen texture {} is {}x{}, expected {}x{}", location,
                            image.getWidth(), image.getHeight(), width, height);
                    return null;
                }
                int[] out = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        out[y * width + x] = image.getPixel(x, y);
                    }
                }
                return out;
            }
        } catch (Exception e) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] screen texture {} could not be read ({})", location,
                    e.getClass().getSimpleName());
            return null;
        }
    }
}
