package za.co.neroland.neroarcade.client.screen;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import za.co.neroland.neroarcade.TestFiles;

/** The shipped screen sheets and font, read with ImageIO the way the client reads them through the resource manager. */
final class ScreenTestAssets {

    private ScreenTestAssets() {
    }

    static Path screenDir() {
        return TestFiles.repoRoot().resolve("common/src/main/resources/assets/neroarcade/textures/screen");
    }

    static int[] argb(Path file) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            int w = image.getWidth();
            int h = image.getHeight();
            int[] out = new int[w * h];
            image.getRGB(0, 0, w, h, out, 0, w);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static SpriteSheets sheets(ThemePalettes.Theme theme) {
        int[][] argb = new int[SpriteSheets.Sheet.values().length][];
        for (SpriteSheets.Sheet sheet : SpriteSheets.Sheet.values()) {
            argb[sheet.ordinal()] = argb(screenDir().resolve(theme.folder()).resolve(sheet.file + ".png"));
        }
        return SpriteSheets.fromArgb(theme, argb);
    }

    static ArcadeFont font() {
        return ArcadeFont.fromArgb(argb(screenDir().resolve("font/arcade5x7.png")));
    }
}
